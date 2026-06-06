package com.xlpvp.mixin;

import com.xlpvp.main.ClassicPvpHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @Unique
    private Entity xlpvp$classicAttackTarget;

    @Unique
    private boolean xlpvp$keepClassicAttackMovement;

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void xlpvp$limitClassicAttack(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        this.xlpvp$classicAttackTarget = null;
        this.xlpvp$keepClassicAttackMovement = false;
        if (target instanceof LivingEntity livingTarget) {
            if (!ClassicPvpHandler.canReachClassicTarget(player, livingTarget)) {
                ci.cancel();
                return;
            }
            if (target instanceof Player && !player.isUsingItem()) {
                this.xlpvp$classicAttackTarget = target;
                this.xlpvp$keepClassicAttackMovement = true;
            }
        }
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void xlpvp$clearClassicAttack(Entity target, CallbackInfo ci) {
        this.xlpvp$classicAttackTarget = null;
        this.xlpvp$keepClassicAttackMovement = false;
    }

    @Redirect(
        method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSprinting()Z", ordinal = 0)
    )
    private boolean xlpvp$useClassicSprintKnockback(Player player) {
        if (this.xlpvp$classicAttackTarget instanceof Player target) {
            return ClassicPvpHandler.hasQueuedSprintKnockback(player, target);
        }
        return player.isSprinting();
    }

    @Redirect(
        method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V")
    )
    private void xlpvp$keepClassicAttackVelocity(Player player, Vec3 movement) {
        if (!this.xlpvp$keepClassicAttackMovement) {
            player.setDeltaMovement(movement);
        }
    }

    @Redirect(
        method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V")
    )
    private void xlpvp$keepClassicAttackSprint(Player player, boolean sprinting) {
        if (!this.xlpvp$keepClassicAttackMovement) {
            player.setSprinting(sprinting);
        }
    }
}
