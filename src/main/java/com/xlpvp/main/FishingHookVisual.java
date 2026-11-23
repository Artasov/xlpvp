package com.xlpvp.main;

import com.xlpvp.trace.ClientDuelTrace;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Per-client presentation. The server alone selects a hooked entity and applies the pull. */
public final class FishingHookVisual {
    private static final int PREDICTION_TICKS = 8;
    private static final int BLEND_TICKS = 3;
    private final Map<Integer, LocalContact> localHits = new HashMap<>();
    @Nullable private HookContact approachContact;
    @Nullable private HookContact metadataContact;
    @Nullable private HookContact lastGoal;
    @Nullable private FramePoint previous;
    @Nullable private FramePoint current;
    @Nullable private AirStep approach;
    private int blendRemaining;

    public void predict(FishingHook hook, Entity target) {
        if (approach != null && approach.target == target) return;
        if (localHits.containsKey(target.getId())) return;
        HookContact hit = HookContact.capture(hook, target);
        Vec3 direction = hook.getDeltaMovement().normalize();
        AABB box = target.getBoundingBox().inflate(0.3F);
        Vec3 farCorner = new Vec3(direction.x >= 0 ? box.maxX : box.minX,
                direction.y >= 0 ? box.maxY : box.minY, direction.z >= 0 ? box.maxZ : box.minZ);
        localHits.put(target.getId(), new LocalContact(hit, hook.tickCount, direction, farCorner.dot(direction)));
        if (hook.level().isClientSide()) ClientDuelTrace.rod("rod_predicted_contact", hook, target.getId());
    }

    public void syncedTarget(FishingHook hook, int syncedId, @Nullable Entity target) {
        // Metadata has the ID but no surface point. Some servers never send the optional payload.
        metadataContact = target == null ? null : HookContact.capture(hook, target);
        localHits.keySet().removeIf(id -> id != syncedId);
        if (approachContact != null && approachContact.targetId() != syncedId) approachContact = null;
    }

    public void confirmedDuringFlight(FishingHook hook, Entity target, Vec3 velocity) {
        if (approach != null || current != null || localHits.containsKey(target.getId())) return;
        AirStep first = new AirStep(target, hook.position(), velocity, 3);
        // Only continue a flight that actually approaches this visible target.
        for (AirStep step = first; step != null; step = step.next()) {
            if (step.inWater(hook)) return;
            HitResult block = step.clipBlocks(hook);
            if (HookContact.traceBody(hook, target, step.start, block.getLocation()) != null) {
                approach = first;
                return;
            }
            if (block.getType() != HitResult.Type.MISS) return;
        }
    }

    public boolean tick(FishingHook hook, @Nullable HookContact serverContact, int syncedId) {
        int confirmedId = serverContact == null ? syncedId : serverContact.targetId();
        if (confirmedId < 0) {
            // The position codec tracks the last actual server movement, even while rendering is interpolated.
            Vec3 serverPosition = hook.getPositionCodec().getBase();
            localHits.values().removeIf(hit -> {
                if (!hit.expired(hook.tickCount, serverPosition)) return false;
                if (hook.level().isClientSide()) ClientDuelTrace.rod("rod_prediction_expired", hook, hit.point.targetId());
                return true;
            });
        } else {
            localHits.keySet().removeIf(id -> id != confirmedId);
        }
        boolean arrived = false;
        if (approach != null && (confirmedId != approach.target.getId() || approach.target.isRemoved() || approach.inWater(hook)
                || localHits.containsKey(confirmedId))) approach = null;
        if (approach != null) {
            HitResult block = approach.clipBlocks(hook);
            HookContact hit = HookContact.traceBody(hook, approach.target, approach.start, block.getLocation());
            if (hit != null) {
                approachContact = hit;
                arrived = true;
            } else if (block.getType() == HitResult.Type.MISS && approach.remaining > 1) {
                previous = current == null ? FramePoint.world(approach.start) : current;
                current = FramePoint.world(approach.start.add(approach.motion));
                approach = approach.next();
                return true;
            }
            approach = null;
        }
        if (metadataContact == null && syncedId >= 0 && serverContact == null) {
            Entity tracked = hook.level().getEntity(syncedId);
            if (tracked != null) metadataContact = HookContact.capture(hook, tracked);
        }
        // A local hit may refine the contact point only after the server has identified the target.
        HookContact goal = confirmedId < 0 ? null : confirmedContact(confirmedId, serverContact);
        Entity target = goal == null ? null : hook.level().getEntity(goal.targetId());
        if (target == null || target.isRemoved()) goal = null;

        if (current == null && goal == null) return confirmedId >= 0 || !localHits.isEmpty();
        previous = current == null ? FramePoint.world(new Vec3(hook.xOld, hook.yOld, hook.zOld)) : current;
        if (!Objects.equals(lastGoal, goal)) {
            blendRemaining = arrived ? 1 : BLEND_TICKS;
        } else if (goal == null && blendRemaining == 0) {
            previous = current = null;
            return confirmedId >= 0 || !localHits.isEmpty();
        }
        lastGoal = goal;
        current = goal == null ? FramePoint.world(hook.position()) : new FramePoint(goal, target, Vec3.ZERO);
        if (blendRemaining > 1) {
            Vec3 difference = previous.at(0).subtract(current.at(0));
            current = new FramePoint(current.contact, current.target,
                    current.offset.add(difference.scale((blendRemaining - 1.0) / blendRemaining)));
        }
        if (blendRemaining > 0) blendRemaining--;
        return true;
    }

    @Nullable
    private HookContact confirmedContact(int id, @Nullable HookContact serverContact) {
        if (approachContact != null && approachContact.targetId() == id) return approachContact;
        LocalContact local = localHits.get(id);
        if (local != null) return local.point;
        if (serverContact != null) return serverContact;
        return metadataContact != null && metadataContact.targetId() == id ? metadataContact : null;
    }

    public boolean isAttachedTo(Entity target) {
        return current != null && current.target == target;
    }

    @Nullable
    public Vec3 position(float partialTick) {
        if (previous == null || current == null) return null;
        return previous.at(partialTick).lerp(current.at(partialTick), partialTick);
    }

    private record LocalContact(HookContact point, int tick, Vec3 direction, double farEdge) {
        boolean expired(int now, Vec3 serverPosition) {
            return now - tick >= PREDICTION_TICKS || serverPosition.dot(direction) > farEdge + 1.0 / 4096;
        }
    }

    private record AirStep(Entity target, Vec3 start, Vec3 motion, int remaining) {
        boolean inWater(FishingHook hook) {
            return hook.level().getFluidState(BlockPos.containing(start)).is(FluidTags.WATER);
        }

        HitResult clipBlocks(FishingHook hook) {
            return hook.level().clip(new ClipContext(start, start.add(motion), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, hook));
        }

        @Nullable AirStep next() {
            return remaining == 1 ? null : new AirStep(target, start.add(motion),
                    motion.add(0, -0.04F, 0).scale(0.92F), remaining - 1);
        }
    }

    // Resolve against the target's rendered pose even when the target ticks after the hook.
    private record FramePoint(@Nullable HookContact contact, @Nullable Entity target, Vec3 offset) {
        static FramePoint world(Vec3 position) { return new FramePoint(null, null, position); }

        Vec3 at(float partialTick) {
            return contact == null ? offset : contact.renderPositionOn(target, partialTick).add(offset);
        }
    }
}
