package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xlpvp.main.FishingHookAttachment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    @Unique private static final String RENDER = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @WrapWithCondition(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;IFIII)V"), require = 4)
    private boolean xlpvp$showBobber(VertexConsumer buffer, PoseStack.Pose pose, int light, float x, int y, int u, int v,
                                     FishingHook hook) {
        Camera camera = Minecraft.getInstance().getEntityRenderDispatcher().camera;
        if (camera == null || camera.isDetached() || !(camera.getEntity() instanceof Player viewer)
                || hook.getOwner() == viewer) return true;
        return !((FishingHookAttachment) hook).xlpvp$isVisuallyAttachedTo(viewer);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            ordinal = 0, shift = At.Shift.AFTER))
    private void xlpvp$moveBobber(FishingHook hook, float yaw, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, int light, CallbackInfo ci) {
        Vec3 position = ((FishingHookAttachment) hook).xlpvp$getVisualPosition(partialTick);
        if (position != null) {
            // LevelRenderer positions the pose stack using xOld; getPosition uses xo instead.
            pose.translate(position.x - Mth.lerp(partialTick, hook.xOld, hook.getX()),
                    position.y - Mth.lerp(partialTick, hook.yOld, hook.getY()),
                    position.z - Mth.lerp(partialTick, hook.zOld, hook.getZ()));
        }
    }

    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/FishingHook;getPosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 xlpvp$moveLineEnd(Vec3 original, FishingHook hook, float yaw, float partialTick) {
        Vec3 position = ((FishingHookAttachment) hook).xlpvp$getVisualPosition(partialTick);
        return position != null ? position : original;
    }
}
