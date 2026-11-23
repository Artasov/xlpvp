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
    private final Map<Integer, HookContact> localHits = new HashMap<>();
    @Nullable private HookContact prediction;
    @Nullable private HookContact metadataContact;
    @Nullable private HookContact lastGoal;
    @Nullable private FramePoint previous;
    @Nullable private FramePoint current;
    @Nullable private AirStep approach;
    private int predictionTick;
    private int blendRemaining;
    private Vec3 castDirection = Vec3.ZERO;
    private double farEdge;

    public void predict(FishingHook hook, Entity target) {
        if (approach != null && approach.target == target) return;
        if (localHits.containsKey(target.getId())) return;
        HookContact hit = HookContact.capture(hook, target);
        localHits.put(target.getId(), hit);
        if (prediction == null) {
            prediction = hit;
            predictionTick = hook.tickCount;
            castDirection = hook.getDeltaMovement().normalize();
            AABB box = target.getBoundingBox().inflate(0.3F);
            Vec3 farCorner = new Vec3(castDirection.x >= 0 ? box.maxX : box.minX,
                    castDirection.y >= 0 ? box.maxY : box.minY, castDirection.z >= 0 ? box.maxZ : box.minZ);
            farEdge = farCorner.dot(castDirection);
            ClientDuelTrace.rod("rod_predicted_contact", hook, target.getId());
        }
    }

    public void syncedTarget(FishingHook hook, int syncedId, @Nullable Entity target) {
        // Metadata has the ID but no surface point. Some servers never send the optional payload.
        metadataContact = target == null ? null : HookContact.capture(hook, target);
        if (prediction != null && syncedId != prediction.targetId()) prediction = null;
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
        boolean arrived = false;
        if (approach != null && (confirmedId != approach.target.getId() || approach.target.isRemoved() || approach.inWater(hook)
                || localHits.containsKey(confirmedId))) approach = null;
        if (approach != null) {
            HitResult block = approach.clipBlocks(hook);
            HookContact hit = HookContact.traceBody(hook, approach.target, approach.start, block.getLocation());
            if (hit != null) {
                localHits.put(confirmedId, hit);
                arrived = true;
            } else if (block.getType() == HitResult.Type.MISS && approach.remaining > 1) {
                previous = current == null ? FramePoint.world(approach.start) : current;
                current = FramePoint.world(approach.start.add(approach.motion));
                approach = approach.next();
                return true;
            }
            approach = null;
        }
        if (prediction != null && confirmedId < 0) {
            // ClientPacketListener only advances this base for an actual server position update.
            Vec3 serverPosition = hook.getPositionCodec().getBase();
            if (serverPosition.dot(castDirection) > farEdge + 1.0 / 4096
                    || hook.tickCount - predictionTick >= PREDICTION_TICKS) {
                ClientDuelTrace.rod("rod_prediction_expired", hook, prediction.targetId());
                prediction = null;
            }
        }
        if (metadataContact == null && syncedId >= 0 && prediction == null && serverContact == null) {
            Entity tracked = hook.level().getEntity(syncedId);
            if (tracked != null) metadataContact = HookContact.capture(hook, tracked);
        }
        HookContact goal = confirmedId >= 0 ? localHits.get(confirmedId) : prediction;
        if (goal == null) goal = serverContact;
        if (goal == null) goal = metadataContact;
        Entity target = goal == null ? null : hook.level().getEntity(goal.targetId());
        if (target == null || target.isRemoved()) goal = null;

        if (current == null && goal == null) return confirmedId >= 0 || !localHits.isEmpty();
        previous = current == null ? FramePoint.world(new Vec3(hook.xOld, hook.yOld, hook.zOld)) : current;
        if (!Objects.equals(lastGoal, goal)) {
            // A local ray hit is the natural endpoint of this tick's flight segment.
            boolean hitThisTick = goal != null && goal == prediction && hook.tickCount == predictionTick;
            blendRemaining = hitThisTick || arrived ? 1 : BLEND_TICKS;
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

    public boolean isAttachedTo(Entity target) {
        return current != null && current.target == target;
    }

    @Nullable
    public Vec3 position(float partialTick) {
        if (previous == null || current == null) return null;
        return previous.at(partialTick).lerp(current.at(partialTick), partialTick);
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
