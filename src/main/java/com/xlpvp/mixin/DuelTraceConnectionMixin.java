package com.xlpvp.mixin;

import com.xlpvp.trace.DuelTrace;
import com.xlpvp.trace.TracePackets;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class DuelTraceConnectionMixin {
    @Shadow public abstract PacketListener getPacketListener();

    // No world reads on the network thread. This is receipt of the decoded packet.
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void xlpvp$receiveTime(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (getPacketListener() instanceof ServerGamePacketListenerImpl game && DuelTrace.active(game.player)) {
            TracePackets.received(packet);
        }
    }

    // Server enqueue time, before the transport writes to the socket.
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V", at = @At("HEAD"))
    private void xlpvp$sendTime(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo ci) {
        if (getPacketListener() instanceof ServerGamePacketListenerImpl game && DuelTrace.active(game.player)
                && game.player.server.isSameThread()) {
            TracePackets.outbound(game.player, packet);
        }
    }
}
