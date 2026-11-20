package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BaseFireBlock.class)
public abstract class PlayerFireContactMixin {
    @ModifyExpressionValue(method = "entityInside", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getRemainingFireTicks()I", ordinal = 0))
    private int xlpvp$igniteOnContact(int fireTicks, BlockState state, Level level, BlockPos pos, Entity entity) {
        if (fireTicks > 0 || !(entity instanceof ServerPlayer player)
                || !(state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE))) return fireTicks;
        if (player.getAbilities().invulnerable || player.hasEffect(MobEffects.FIRE_RESISTANCE)
                || player.isInvulnerableTo(level.damageSources().inFire())
                || player.isInWaterRainOrBubble() || player.isInPowderSnow) return fireTicks;
        // The block increments this counter and ignites at zero, through Arclight's cancellable event.
        return -1;
    }
}
