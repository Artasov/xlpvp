package com.xlpvp.main;

import java.util.regex.Pattern;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Tracking rules for the UUID-named runtime worlds created by xl-minigames. */
public final class ArenaTracking {
    private static final Pattern WORLD_NAME = Pattern.compile(
            "(duel|hg|sw|hgm|swm)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final double DISCONTINUITY_DISTANCE_SQUARED = 16.0;

    private ArenaTracking() {}

    public static boolean isArenaWorld(Entity entity) {
        return isArenaPath(entity.level().dimension().location().getPath());
    }

    static boolean isArenaPath(String path) {
        return WORLD_NAME.matcher(path.substring(path.lastIndexOf('/') + 1)).matches();
    }

    public static boolean isDiscontinuous(Vec3 lastTrackedPosition, double x, double y, double z) {
        return lastTrackedPosition.distanceToSqr(x, y, z) > DISCONTINUITY_DISTANCE_SQUARED;
    }

}
