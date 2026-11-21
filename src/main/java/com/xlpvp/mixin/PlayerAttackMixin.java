package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xlpvp.main.ClassicPvpHandler;
import com.xlpvp.trace.DuelTrace;
import net.minecraft.world.damagesource.DamageSource;
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

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean xlpvp$traceDirectDamage(Entity target, DamageSource source, float amount, Operation<Boolean> hurt) {
        Player attacker = (Player) (Object) this;
        if (attacker.level().isClientSide() || !(target instanceof Player opponent) || !DuelTrace.pair(attacker, target)) {
            return hurt.call(target, source, amount);
        }
        float health = opponent.getHealth();
        float absorption = opponent.getAbsorptionAmount();
        int invulnerableTicks = opponent.invulnerableTime;
        boolean accepted = hurt.call(target, source, amount);
        DuelTrace.event(attacker, target, "melee_damage_result", "accepted", accepted, "requested", amount,
                "healthBefore", health, "healthAfter", opponent.getHealth(), "absorptionBefore", absorption,
                "absorptionAfter", opponent.getAbsorptionAmount(), "invulnerableTicksBefore", invulnerableTicks);
        return accepted;
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
