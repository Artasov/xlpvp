package com.xlpvp.mixin;

import com.xlpvp.client.ClientDuelTraceView;
import com.xlpvp.client.ClassicPvpInput;
import com.xlpvp.trace.ClientDuelTrace;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ClientDuelTraceRenderMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V", shift = At.Shift.AFTER))
    private void xlpvp$captureFramePick(DeltaTracker delta, CallbackInfo ci) {
        Minecraft game = Minecraft.getInstance();
        float partialTick = delta.getGameTimeDeltaPartialTick(true);
        ClassicPvpInput.render(game, partialTick);
        if (ClientDuelTrace.active()) {
            ClientDuelTraceView.render(game, partialTick);
        }
    }
}
