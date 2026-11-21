package com.xlpvp.mixin;

import com.xlpvp.trace.DuelTrace;
import com.xlpvp.trace.TracePackets;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketUtils.class)
public abstract class DuelTracePacketUtilsMixin {
    // The network-thread invocation throws; only the server-thread invocation returns.
    @Inject(method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V", at = @At("RETURN"))
    private static void xlpvp$processPacket(Packet<?> packet, PacketListener listener, BlockableEventLoop<?> loop, CallbackInfo ci) {
        if (listener instanceof ServerGamePacketListenerImpl game && DuelTrace.active(game.player) && loop.isSameThread()) {
            TracePackets.inbound(game.player, packet);
        }
    }
}
