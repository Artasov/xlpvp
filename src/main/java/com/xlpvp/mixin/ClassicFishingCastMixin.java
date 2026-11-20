package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class ClassicFishingCastMixin {
    @Unique private static final String CAST = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V";
    @Unique private boolean xlpvp$airborneCast;

    @WrapOperation(method = CAST, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/FishingHook;moveTo(DDDFF)V"))
    private void xlpvp$castFromHand(FishingHook hook, double x, double y, double z, float yaw, float pitch,
                                    Operation<Void> move, Player owner) {
        float angle = yaw / 180.0F * (float) Math.PI;
        // A crouched 1.7 player cast at the standing height. Use the current eyes for newer poses.
        double eyeY = owner.hasPose(Pose.CROUCHING) ? owner.getY() + owner.getEyeHeight(Pose.STANDING) : owner.getEyeY();
        move.call(hook, owner.getX() - Mth.cos(angle) * 0.16F, eyeY - 0.1F,
                owner.getZ() - Mth.sin(angle) * 0.16F, yaw, pitch);
    }

    @ModifyExpressionValue(method = CAST, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 xlpvp$classicLaunch(Vec3 modernVelocity, Player owner) {
        float yaw = owner.getYRot() / 180.0F * (float) Math.PI;
        float pitch = owner.getXRot() / 180.0F * (float) Math.PI;
        double x = -Mth.sin(yaw) * Mth.cos(pitch) * 0.4F;
        double y = -Mth.sin(pitch) * 0.4F;
        double z = Mth.cos(yaw) * Mth.cos(pitch) * 0.4F;
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        double spread = 0.0075F;
        RandomSource random = ((FishingHook) (Object) this).getRandom();
        return new Vec3(x / length + random.nextGaussian() * spread, y / length + random.nextGaussian() * spread,
                z / length + random.nextGaussian() * spread).scale(1.5F);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void xlpvp$beginTick(CallbackInfo ci) {
        xlpvp$airborneCast = false;
    }

    @Inject(method = "checkCollision", at = @At("HEAD"))
    private void xlpvp$beginAirStep(CallbackInfo ci) {
        // Vanilla calls checkCollision only in FLYING state, outside water, before moving the hook.
        FishingHook hook = (FishingHook) (Object) this;
        xlpvp$airborneCast = !hook.onGround() && !hook.horizontalCollision;
    }

    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = -0.03D))
    private double xlpvp$deferAirGravity(double gravity) {
        return xlpvp$airborneCast ? 0.0 : -0.02;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 xlpvp$finishAirStep(Vec3 motion, double drag, Operation<Vec3> scale) {
        FishingHook hook = (FishingHook) (Object) this;
        if (xlpvp$airborneCast && !hook.onGround() && !hook.horizontalCollision) {
            // 1.7.10 moves by the full velocity, then applies gravity, then drag (including to Y).
            return scale.call(motion.add(0, -0.04F, 0), (double) 0.92F);
        }
        return scale.call(motion, drag);
    }
}
