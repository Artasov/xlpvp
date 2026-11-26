package com.xlpvp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.xlpvp.main.ArenaTracking;
import com.xlpvp.trace.ClientDuelTrace;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class ClientArenaTrackingMixin {
    @Unique
    private static final int xlpvp$ARENA_SMOOTHING_TICKS = 3;

    @WrapOperation(method = "handleTeleportEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;syncPacketPositionCodec(DDD)V"))
    private void xlpvp$compareAbsoluteAgainstNetworkBaseline(Entity entity, double x, double y, double z,
                                                            Operation<Void> sync,
                                                            @Share("xlpvp$discontinuous") LocalBooleanRef discontinuous) {
        discontinuous.set(xlpvp$isArenaRemotePlayer(entity)
                && ArenaTracking.isDiscontinuous(entity.getPositionCodec().getBase(), x, y, z));
        sync.call(entity, x, y, z);
    }

    @WrapOperation(method = "handleTeleportEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFI)V"))
    private void xlpvp$applyArenaAbsolutePosition(Entity entity, double x, double y, double z, float yaw, float pitch,
                                                int steps, Operation<Void> lerp,
                                                @Share("xlpvp$discontinuous") LocalBooleanRef discontinuous) {
        if (discontinuous.get()) {
            xlpvp$traceArenaTrack(entity, x, y, z, 0, "snap");
            entity.absMoveTo(x, y, z, yaw, pitch);
            entity.setOldPosAndRot();
            lerp.call(entity, x, y, z, yaw, pitch, 0);
        } else {
            int smoothing = xlpvp$isArenaRemotePlayer(entity) ? xlpvp$ARENA_SMOOTHING_TICKS : steps;
            xlpvp$traceArenaTrack(entity, x, y, z, smoothing, "absolute");
            lerp.call(entity, x, y, z, yaw, pitch, smoothing);
        }
    }

    @WrapOperation(method = "handleMoveEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFI)V"))
    private void xlpvp$applyArenaRelativePosition(Entity entity, double x, double y, double z, float yaw,
                                                float pitch, int steps, Operation<Void> lerp,
                                                @Local(argsOnly = true) ClientboundMoveEntityPacket packet) {
        int smoothing = xlpvp$isArenaRemotePlayer(entity) ? xlpvp$ARENA_SMOOTHING_TICKS : steps;
        if (packet.hasPosition()) xlpvp$traceArenaTrack(entity, x, y, z, smoothing, "relative");
        lerp.call(entity, x, y, z, yaw, pitch, smoothing);
    }

    @Unique
    private static void xlpvp$traceArenaTrack(Entity entity, double x, double y, double z, int steps, String packet) {
        if (ClientDuelTrace.active() && entity.getUUID().equals(ClientDuelTrace.opponent())
                && xlpvp$isArenaRemotePlayer(entity)) {
            ClientDuelTrace.event("arena_track_in", "targetEntityId", entity.getId(), "packet", packet, "steps", steps,
                    "renderedX", entity.getX(), "renderedY", entity.getY(), "renderedZ", entity.getZ(),
                    "x", x, "y", y, "z", z);
        }
    }

    @Unique
    private static boolean xlpvp$isArenaRemotePlayer(Entity entity) {
        return entity instanceof RemotePlayer && ArenaTracking.isArenaWorld(entity);
    }
}
