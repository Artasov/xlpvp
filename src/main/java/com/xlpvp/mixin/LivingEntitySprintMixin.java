package com.xlpvp.mixin;

import com.xlpvp.main.ClassicPvpHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySprintMixin {
    @Inject(method = "setSprinting", at = @At("HEAD"))
    private void xlpvp$resetSprintHit(boolean sprinting, CallbackInfo ci) {
        if (!sprinting && (Object) this instanceof ServerPlayer player && player.isSprinting()) {
            ClassicPvpHandler.onSprintStopped(player);
        }
    }
}
