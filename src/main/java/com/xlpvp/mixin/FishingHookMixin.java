package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.xlpvp.main.FishingHookAttachment;
import com.xlpvp.main.HookContact;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin implements FishingHookAttachment {
    @Shadow @Final private static EntityDataAccessor<Integer> DATA_HOOKED_ENTITY;
    @Shadow @Nullable private Entity hookedIn;
    @Unique @Nullable private HookContact xlpvp$contact;

    @Override
    public HookContact xlpvp$getContact() {
        return xlpvp$contact;
    }

    @Override
    public void xlpvp$setContact(HookContact contact) {
        xlpvp$contact = contact;
        FishingHook hook = (FishingHook) (Object) this;
        hookedIn = hook.level().getEntity(contact.targetId());
        xlpvp$followContact(hook);
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void xlpvp$captureContact(EntityHitResult result, CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.level() instanceof ServerLevel level && hookedIn == result.getEntity()) {
            xlpvp$contact = HookContact.capture(hook, hookedIn);
            level.getChunkSource().chunkMap.getPlayersWatching(hook).forEach(xlpvp$contact::sendTo);
        }
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("RETURN"))
    private void xlpvp$clearOldContact(EntityDataAccessor<?> key, CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (key == DATA_HOOKED_ENTITY && xlpvp$contact != null
                && hook.getEntityData().get(DATA_HOOKED_ENTITY) != xlpvp$contact.targetId() + 1) {
            xlpvp$contact = null;
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void xlpvp$positionAfterTick(CallbackInfo ci) {
        if (xlpvp$contact == null) return;
        FishingHook hook = (FishingHook) (Object) this;
        if (hookedIn == null && hook.level().isClientSide()) {
            // A distant observer may start tracking the victim after the bobber.
            hookedIn = hook.level().getEntity(xlpvp$contact.targetId());
        }
        xlpvp$followContact(hook);
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

    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = -0.03D))
    private double xlpvp$useClassicRodGravity(double gravity) {
        return -0.02D;
    }
}
