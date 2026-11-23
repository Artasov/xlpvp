package com.xlpvp.trace;

import com.xlpvp.Core;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.UUID;

/** Optional trace control. The acknowledgement records a boundary in the client's packet stream. */
@EventBusSubscriber(modid = Core.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DuelTracePackets {
    private DuelTracePackets() {}

    public record Control(UUID trace, UUID duel, UUID opponent, boolean start) implements CustomPacketPayload {
        private static final Type<Control> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Core.MODID, "duel_trace_control"));
        private static final StreamCodec<ByteBuf, Control> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Control::trace,
                UUIDUtil.STREAM_CODEC, Control::duel,
                UUIDUtil.STREAM_CODEC, Control::opponent,
                ByteBufCodecs.BOOL, Control::start,
                Control::new);

        @Override public Type<Control> type() { return TYPE; }

        boolean sendTo(ServerPlayer player) {
            if (!player.connection.isAcceptingMessages() || !player.connection.hasChannel(TYPE)) return false;
            player.connection.send(this);
            return true;
        }
    }

    public record Ready(UUID trace) implements CustomPacketPayload {
        private static final Type<Ready> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Core.MODID, "duel_trace_ready"));
        private static final StreamCodec<ByteBuf, Ready> CODEC = UUIDUtil.STREAM_CODEC.map(Ready::new, Ready::trace);

        @Override public Type<Ready> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional()
                .playToClient(Control.TYPE, Control.CODEC, (signal, context) -> ClientDuelTrace.control(signal))
                .playToServer(Ready.TYPE, Ready.CODEC, (ready, context) -> {
                    if (context.player() instanceof ServerPlayer player) DuelTrace.clientReady(player, ready.trace());
                });
    }
}
