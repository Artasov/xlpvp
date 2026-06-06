package com.xlpvp.mixin;

import com.xlpvp.main.ClassicPvpHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void xlpvp$limitClassicAttack(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (target instanceof LivingEntity livingTarget) {
            if (!ClassicPvpHandler.canReachClassicTarget(player, livingTarget)) {
                ci.cancel();
            }
        }
    }
}
