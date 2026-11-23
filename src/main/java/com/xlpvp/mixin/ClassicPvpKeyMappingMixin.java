package com.xlpvp.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.xlpvp.client.ClassicPvpInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class ClassicPvpKeyMappingMixin {
    @Inject(method = "click", at = @At("HEAD"))
    private static void xlpvp$captureAttackClick(InputConstants.Key key, CallbackInfo ci) {
        Minecraft game = Minecraft.getInstance();
        if (game != null && game.player != null && game.screen == null && game.getOverlay() == null
                && game.options.keyAttack.isActiveAndMatches(key)) ClassicPvpInput.queueClick();
    }

    @Inject(method = "releaseAll", at = @At("HEAD"))
    private static void xlpvp$discardAttackClicks(CallbackInfo ci) {
        ClassicPvpInput.clearClicks();
    }
}
