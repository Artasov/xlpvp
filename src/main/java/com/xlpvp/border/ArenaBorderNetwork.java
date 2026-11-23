package com.xlpvp.border;

import com.xlpvp.Core;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Sends a visual arena border without changing the world's physical border. */
@EventBusSubscriber(modid = Core.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ArenaBorderNetwork {
    private ArenaBorderNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().executesOn(HandlerThread.MAIN)
                .playToClient(Wall.TYPE, Wall.CODEC, (wall, context) -> ArenaBorderClient.receive(context.player(), wall));
    }

    public static boolean supports(ServerPlayer player) {
        return player != null && player.connection != null && player.connection.isAcceptingMessages()
                && player.connection.hasChannel(Wall.TYPE);
    }

    public static boolean show(ServerPlayer player, double x, double z, double size, double targetSize, int seconds) {
        if (!supports(player)) return false;
        Wall wall = new Wall(player.level().dimension().location(), x, z, size, targetSize, seconds);
        if (!wall.valid()) return false;
        player.connection.send(wall);
        return true;
    }

    public static void clear(ServerPlayer player) {
        if (supports(player)) player.connection.send(new Wall(player.level().dimension().location(), 0, 0, 0, 0, 0));
    }

    record Wall(ResourceLocation dimension, double x, double z, double size, double targetSize, int seconds)
            implements CustomPacketPayload {
        private static final Type<Wall> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Core.MODID, "arena_border"));
        private static final StreamCodec<ByteBuf, Wall> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Wall::dimension,
                ByteBufCodecs.DOUBLE, Wall::x,
                ByteBufCodecs.DOUBLE, Wall::z,
                ByteBufCodecs.DOUBLE, Wall::size,
                ByteBufCodecs.DOUBLE, Wall::targetSize,
                ByteBufCodecs.VAR_INT, Wall::seconds,
                Wall::new);

        boolean valid() {
            return Math.abs(x) <= WorldBorder.MAX_CENTER_COORDINATE && Math.abs(z) <= WorldBorder.MAX_CENTER_COORDINATE
                    && size > 0 && size <= WorldBorder.MAX_SIZE
                    && targetSize > 0 && targetSize <= WorldBorder.MAX_SIZE && seconds >= 0;
        }

        @Override
        public Type<Wall> type() { return TYPE; }
    }
}
