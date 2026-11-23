package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xlpvp.client.ClientDuelTraceView;
import com.xlpvp.trace.ClientDuelTrace;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class ClientDuelTraceInputMixin {
    @Shadow protected int missTime;
    @Unique private boolean xlpvp$usingAtKeybindStart;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void xlpvp$readUsingItem(CallbackInfo ci) {
        if (ClientDuelTrace.active()) {
            Minecraft game = (Minecraft) (Object) this;
            xlpvp$usingAtKeybindStart = game.player != null && game.player.isUsingItem();
        }
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
    private boolean xlpvp$traceCombatKey(KeyMapping key, Operation<Boolean> consume) {
        boolean clicked = consume.call(key);
        if (clicked && ClientDuelTrace.active()) {
            Minecraft game = (Minecraft) (Object) this;
            if (key == game.options.keyAttack || key == game.options.keyUse) {
                ClientDuelTrace.event("key_consumed", "key", key == game.options.keyAttack ? "attack" : "use",
                        "missTime", missTime, "usingAtKeybindStart", xlpvp$usingAtKeybindStart,
                        "usingItem", game.player != null && game.player.isUsingItem());
            }
        }
        return clicked;
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void xlpvp$traceAttackStart(CallbackInfoReturnable<Boolean> cir) {
        if (!ClientDuelTrace.active()) return;
        Minecraft game = (Minecraft) (Object) this;
        String gate = missTime > 0 ? "miss_time"
                : game.hitResult == null ? "no_pick"
                : game.player.isHandsBusy() ? "hands_busy" : "open";
        ClientDuelTraceView.attack(game, missTime, gate);
    }

    @ModifyExpressionValue(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;onClickInput(ILnet/minecraft/client/KeyMapping;Lnet/minecraft/world/InteractionHand;)Lnet/neoforged/neoforge/client/event/InputEvent$InteractionKeyMappingTriggered;",
            remap = false))
    private InputEvent.InteractionKeyMappingTriggered xlpvp$traceAttackCancellation(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientDuelTrace.active() && event.isCanceled()) {
            ClientDuelTrace.event("attack_input_cancelled", "wouldSwing", event.shouldSwingHand());
        }
        return event;
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void xlpvp$traceAttackEnd(CallbackInfoReturnable<Boolean> cir) {
        if (ClientDuelTrace.active()) {
            ClientDuelTrace.event("attack_end", "missTimeAfter", missTime, "destroyedBlock", cir.getReturnValue());
        }
    }
}
