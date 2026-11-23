package com.xlpvp.mixin;

import com.xlpvp.trace.ClientDuelTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class ClientDuelTraceMouseMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void xlpvp$traceCombatMouse(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (!ClientDuelTrace.active()) return;
        Minecraft game = Minecraft.getInstance();
        if (window != game.getWindow().getWindow()) return;
        boolean attack = game.options.keyAttack.matchesMouse(button);
        boolean use = game.options.keyUse.matchesMouse(button);
        if (attack || use) {
            ClientDuelTrace.event("mouse_combat", "attack", attack, "use", use, "pressed", action == 1,
                    "mouseGrabbed", game.mouseHandler.isMouseGrabbed(), "screenOpen", game.screen != null,
                    "usingItem", game.player != null && game.player.isUsingItem());
        }
    }
}
