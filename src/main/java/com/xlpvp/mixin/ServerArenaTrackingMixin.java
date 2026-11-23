package com.xlpvp.mixin;

import com.xlpvp.main.ArenaTracking;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public abstract class ServerArenaTrackingMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final @Mutable private int updateInterval;
    @Shadow @Final private VecDeltaCodec positionCodec;
    @Shadow private int teleportDelay;
    @Unique private boolean xlpvp$arenaPlayer;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xlpvp$trackArenaPlayersEveryTick(ServerLevel level, Entity entity, int interval,
                                                 boolean trackDelta, Consumer<Packet<?>> broadcast, CallbackInfo ci) {
        xlpvp$arenaPlayer = entity instanceof ServerPlayer && ArenaTracking.isArenaWorld(entity);
        if (xlpvp$arenaPlayer) updateInterval = 1;
    }

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void xlpvp$sendLargeArenaRelocationsAsAbsolute(CallbackInfo ci) {
        if (!xlpvp$arenaPlayer || entity.isPassenger()) return;
        var pos = entity.trackingPosition();
        if (ArenaTracking.isDiscontinuous(positionCodec.getBase(), pos.x, pos.y, pos.z)) {
            // Use the vanilla absolute branch, which also updates the delta baseline.
            teleportDelay = 400;
        }
    }
}
