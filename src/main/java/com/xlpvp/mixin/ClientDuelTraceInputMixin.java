package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xlpvp.client.ClientDuelTraceView;
import com.xlpvp.client.ClassicPvpInput;
import com.xlpvp.trace.ClientDuelTrace;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import org.objectweb.asm.Opcodes;
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
    @Unique private ClassicPvpInput.Click xlpvp$consumedAttack;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void xlpvp$readUsingItem(CallbackInfo ci) {
        xlpvp$consumedAttack = null;
        if (ClientDuelTrace.active()) {
            Minecraft game = (Minecraft) (Object) this;
            xlpvp$usingAtKeybindStart = game.player != null && game.player.isUsingItem();
        }
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
    private boolean xlpvp$traceCombatKey(KeyMapping key, Operation<Boolean> consume) {
        boolean clicked = consume.call(key);
        Minecraft game = (Minecraft) (Object) this;
        if (key == game.options.keyAttack) {
            xlpvp$consumedAttack = clicked ? ClassicPvpInput.consumeClick() : null;
            if (!clicked) ClassicPvpInput.clearClicks();
        }
        if (clicked && ClientDuelTrace.active()) {
            if (key == game.options.keyAttack || key == game.options.keyUse) {
                ClientDuelTrace.event("key_consumed", "key", key == game.options.keyAttack ? "attack" : "use",
                        "missTime", missTime, "usingAtKeybindStart", xlpvp$usingAtKeybindStart,
                        "usingItem", game.player != null && game.player.isUsingItem());
            }
        }
        return clicked;
    }

    @WrapMethod(method = "startAttack")
    private boolean xlpvp$useClickAim(Operation<Boolean> attack) {
        Minecraft game = (Minecraft) (Object) this;
        ClassicPvpInput.Click click = xlpvp$consumedAttack;
        xlpvp$consumedAttack = null;
        EntityHitResult recovered = ClassicPvpInput.recoverPlayerPick(game, click);
        if (recovered == null) return attack.call();
        HitResult originalHit = game.hitResult;
        Entity originalEntity = game.crosshairPickEntity;
        game.hitResult = recovered;
        game.crosshairPickEntity = recovered.getEntity();
        try {
            if (ClientDuelTrace.active()) ClientDuelTrace.event("attack_frame_pick_recovered", "targetId", recovered.getEntity().getId());
            return attack.call();
        } finally {
            if (game.hitResult == recovered) game.hitResult = originalHit;
            if (game.crosshairPickEntity == recovered.getEntity()) game.crosshairPickEntity = originalEntity;
        }
    }

    @ModifyExpressionValue(method = "startAttack", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;missTime:I", opcode = Opcodes.GETFIELD))
    private int xlpvp$allowConfirmedPlayerPick(int remaining) {
        if (remaining <= 0 || !ClassicPvpInput.canIgnoreMissTime((Minecraft) (Object) this)) return remaining;
        if (ClientDuelTrace.active()) ClientDuelTrace.event("attack_miss_time_bypassed", "missTime", remaining);
        return 0;
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void xlpvp$traceAttackStart(CallbackInfoReturnable<Boolean> cir) {
        if (!ClientDuelTrace.active()) return;
        Minecraft game = (Minecraft) (Object) this;
        String gate = missTime > 0 && !ClassicPvpInput.canIgnoreMissTime(game) ? "miss_time"
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
