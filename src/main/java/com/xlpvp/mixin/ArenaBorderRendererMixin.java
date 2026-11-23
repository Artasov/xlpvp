package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xlpvp.border.ArenaBorderClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class ArenaBorderRendererMixin {
    @WrapOperation(method = "renderWorldBorder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getWorldBorder()Lnet/minecraft/world/level/border/WorldBorder;"))
    private WorldBorder xlpvp$renderArenaBorder(ClientLevel level, Operation<WorldBorder> original) {
        WorldBorder border = ArenaBorderClient.forRendering(level);
        return border == null ? original.call(level) : border;
    }
}
