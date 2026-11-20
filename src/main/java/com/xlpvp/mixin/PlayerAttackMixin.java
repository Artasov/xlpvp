package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.xlpvp.main.ClassicPvpHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @WrapMethod(method = "attack")
    private void xlpvp$classicAttack(Entity target, Operation<Void> original) {
        ClassicPvpHandler.performAttack((Player) (Object) this, target, original);
    }

    @ModifyExpressionValue(
        method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getKnockback(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)F")
    )
    private float xlpvp$applyClassicHit(float weaponKnockback, Entity target) {
        ClassicPvpHandler.onSuccessfulAttack((Player) (Object) this, target, weaponKnockback);
        return weaponKnockback;
    }

    @Redirect(
        method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSprinting()Z", ordinal = 0)
    )
    private boolean xlpvp$useClassicSprintKnockback(Player player) {
        return ClassicPvpHandler.useSprintKnockback(player);
    }

    @Redirect(
        method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V")
    )
    private void xlpvp$keepClassicAttackVelocity(Player player, Vec3 movement) {
        if (!ClassicPvpHandler.isClassicAttack(player)) {
            player.setDeltaMovement(movement);
        }
    }

    @Redirect(
        method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V")
    )
    private void xlpvp$keepClassicAttackSprint(Player player, boolean sprinting) {
        if (!ClassicPvpHandler.isClassicAttack(player)) {
            player.setSprinting(sprinting);
        }
    }
}
