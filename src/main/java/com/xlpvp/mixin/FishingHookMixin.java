package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.xlpvp.main.FishingHookAttachment;
import com.xlpvp.main.FishingHookVisual;
import com.xlpvp.main.HookContact;
import com.xlpvp.trace.ClientDuelTrace;
import com.xlpvp.trace.DuelTrace;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin implements FishingHookAttachment {
    @Shadow @Final private static EntityDataAccessor<Integer> DATA_HOOKED_ENTITY;
    @Shadow @Nullable private Entity hookedIn;
    @Unique @Nullable private HookContact xlpvp$contact;
    @Unique @Nullable private FishingHookVisual xlpvp$visual;
    @Unique @Nullable private Vec3 xlpvp$lastAirMotion;
    @Unique private boolean xlpvp$checkedFlight;

    @Override
    public HookContact xlpvp$getContact() {
        return xlpvp$contact;
    }

    @Override
    public void xlpvp$setContact(HookContact contact) {
        xlpvp$contact = contact;
        // Network handlers can run between two renders of the same tick.
        // Consume the contact in tick(), keeping the displayed segment intact.
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.level().isClientSide()) ClientDuelTrace.rod("rod_server_contact", hook, contact.targetId());
        xlpvp$sampleIncomingTarget(hook, hook.level().getEntity(contact.targetId()));
    }

    @Override
    public Vec3 xlpvp$getVisualPosition(float partialTick) {
        return xlpvp$visual == null ? null : xlpvp$visual.position(partialTick);
    }

    @Override
    public boolean xlpvp$isVisuallyAttachedTo(Entity target) {
        return xlpvp$visual == null ? hookedIn == target : xlpvp$visual.isAttachedTo(target);
    }

    @Unique
    private FishingHookVisual xlpvp$visual() {
        if (xlpvp$visual == null) xlpvp$visual = new FishingHookVisual();
        return xlpvp$visual;
    }

    @Unique
    private void xlpvp$sampleIncomingTarget(FishingHook hook, @Nullable Entity target) {
        FishingHookVisual visual = xlpvp$visual();
        if (target != null && xlpvp$lastAirMotion != null) {
            // An earlier velocity packet can already have zeroed the actual hook.
            visual.confirmedDuringFlight(hook, target, xlpvp$lastAirMotion);
        }
    }

    @Inject(method = "checkCollision", at = @At("HEAD"))
    private void xlpvp$observeAirTick(CallbackInfo ci) {
        xlpvp$checkedFlight = true;
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.getOwner() instanceof ServerPlayer owner && DuelTrace.active(owner)) {
            Vec3 motion = hook.getDeltaMovement();
            DuelTrace.event(owner, null, "rod_flight", "hook", hook.getId(), "ageTicks", hook.tickCount,
                    "x", hook.getX(), "y", hook.getY(), "z", hook.getZ(),
                    "vx", motion.x, "vy", motion.y, "vz", motion.z);
        }
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void xlpvp$captureContact(EntityHitResult result, CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.level().isClientSide()) {
            xlpvp$visual().predict(hook, result.getEntity());
        } else if (hook.level() instanceof ServerLevel level && hookedIn == result.getEntity()) {
            xlpvp$contact = HookContact.capture(hook, hookedIn);
            level.getChunkSource().chunkMap.getPlayersWatching(hook).forEach(xlpvp$contact::sendTo);
        }
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("RETURN"))
    private void xlpvp$clearOldContact(EntityDataAccessor<?> key, CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (key != DATA_HOOKED_ENTITY) return;
        int syncedId = hook.getEntityData().get(DATA_HOOKED_ENTITY) - 1;
        if (xlpvp$contact != null && syncedId != xlpvp$contact.targetId()) {
            xlpvp$contact = null;
        }
        if (hook.level().isClientSide() && (syncedId >= 0 || xlpvp$visual != null)) {
            ClientDuelTrace.rod("rod_server_target", hook, syncedId);
            xlpvp$sampleIncomingTarget(hook, hookedIn);
            xlpvp$visual().syncedTarget(hook, syncedId, hookedIn);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void xlpvp$positionAfterTick(CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.level().isClientSide()) {
            xlpvp$lastAirMotion = xlpvp$checkedFlight && !hook.onGround() && !hook.horizontalCollision
                    && hookedIn == null && xlpvp$contact == null ? hook.getDeltaMovement() : null;
        }
        xlpvp$checkedFlight = false;
        if (xlpvp$contact != null) {
            if (hook.level().isClientSide() && (hookedIn == null || hookedIn.getId() != xlpvp$contact.targetId())) {
                // A distant observer may start tracking the victim after the bobber.
                hookedIn = hook.level().getEntity(xlpvp$contact.targetId());
            }
            xlpvp$followContact(hook);
        }
        if (xlpvp$visual != null && !xlpvp$visual.tick(hook, xlpvp$contact, hook.getEntityData().get(DATA_HOOKED_ENTITY) - 1)) {
            xlpvp$visual = null;
        }
    }

    @Unique
    private void xlpvp$followContact(FishingHook hook) {
        if (hookedIn != null && hookedIn.getId() == xlpvp$contact.targetId() && !hookedIn.isRemoved()) {
            hook.setPos(xlpvp$contact.positionOn(hookedIn));
            hook.setDeltaMovement(Vec3.ZERO);
        }
    }

    @ModifyExpressionValue(method = "pullEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 xlpvp$preserveRetrievalImpulse(Vec3 impulse, Entity target) {
        if (xlpvp$contact == null || target != hookedIn) return impulse;
        // Retrieval used the vanilla bobber position at 80% of the target's height.
        FishingHook hook = (FishingHook) (Object) this;
        Vec3 vanillaPosition = new Vec3(target.getX(), target.getY(0.8), target.getZ());
        return impulse.add(hook.position().subtract(vanillaPosition).scale(0.1));
    }

}
