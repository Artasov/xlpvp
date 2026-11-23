package com.xlpvp.mixin;

import com.xlpvp.trace.ClientDuelTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ClientDuelTraceConnectionMixin {
    @Shadow public abstract PacketListener getPacketListener();

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V", at = @At("HEAD"))
    private void xlpvp$traceClientSend(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo ci) {
        if (!ClientDuelTrace.active() || !(getPacketListener() instanceof ClientPacketListener)) return;
        Minecraft game = Minecraft.getInstance();
        if (!game.isSameThread() || game.player == null) return;
        if (packet instanceof ServerboundInteractPacket interact) {
            interact.dispatch(new ServerboundInteractPacket.Handler() {
                @Override public void onInteraction(InteractionHand hand) {}
                @Override public void onInteraction(InteractionHand hand, Vec3 point) {}
                @Override public void onAttack() {
                    int targetId = ((ClientDuelTraceInteractAccessor) (Object) interact).xlpvp$targetId();
                    ClientDuelTrace.event("out_attack", "targetId", targetId,
                            "sneaking", interact.isUsingSecondaryAction());
                }
            });
        } else if (packet instanceof ServerboundSwingPacket swing) {
            ClientDuelTrace.event("out_swing", "hand", swing.getHand().name());
        } else if (packet instanceof ServerboundSetCarriedItemPacket hotbar) {
            ClientDuelTrace.event("out_hotbar", "slot", hotbar.getSlot());
        } else if (packet instanceof ServerboundUseItemPacket use) {
            xlpvp$rodUse(game, use.getHand(), use.getSequence(), false);
        } else if (packet instanceof ServerboundUseItemOnPacket use) {
            xlpvp$rodUse(game, use.getHand(), use.getSequence(), true);
        } else if (packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            ClientDuelTrace.event("out_release_item");
        }
    }

    @Unique
    private static void xlpvp$rodUse(Minecraft game, InteractionHand hand, int sequence, boolean onBlock) {
        if (game.player.getItemInHand(hand).getItem() instanceof FishingRodItem) {
            ClientDuelTrace.event("out_use_rod", "hand", hand.name(), "sequence", sequence, "onBlock", onBlock,
                    "hookBefore", game.player.fishing == null ? null : game.player.fishing.getId());
        }
    }
}
