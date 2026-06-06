package com.xlpvp.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = -0.03D))
    private double xlpvp$useClassicRodGravity(double gravity) {
        return -0.02D;
    }
}
