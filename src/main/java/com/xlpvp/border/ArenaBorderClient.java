package com.xlpvp.border;

import java.lang.ref.WeakReference;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.border.WorldBorder;

public final class ArenaBorderClient {
    @Nullable private static ActiveWall active;

    private ArenaBorderClient() {}

    static void receive(Player player, ArenaBorderNetwork.Wall wall) {
        if (!(player.level() instanceof ClientLevel level) || !wall.dimension().equals(level.dimension().location())) return;
        if (wall.size() == 0) {
            active = null;
        } else if (wall.valid()) {
            WorldBorder renderBorder = new WorldBorder();
            renderBorder.setCenter(wall.x(), wall.z());
            if (wall.seconds() > 0 && wall.size() != wall.targetSize()) {
                renderBorder.lerpSizeBetween(wall.size(), wall.targetSize(), 1_000L * wall.seconds());
            } else {
                renderBorder.setSize(wall.size());
            }
            active = new ActiveWall(new WeakReference<>(level), renderBorder);
        }
    }

    @Nullable
    public static WorldBorder forRendering(ClientLevel level) {
        if (active == null) return null;
        if (active.level().get() != level) {
            active = null;
            return null;
        }
        WorldBorder renderBorder = active.border();
        renderBorder.tick();
        return renderBorder;
    }

    private record ActiveWall(WeakReference<ClientLevel> level, WorldBorder border) {}
}
