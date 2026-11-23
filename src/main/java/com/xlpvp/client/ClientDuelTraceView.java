package com.xlpvp.client;

import com.xlpvp.trace.ClientDuelTrace;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Holds the latest render pick so only clicks, not every frame, reach disk. */
public final class ClientDuelTraceView {
    private record Frame(long capturedAt, UUID opponent, float partialTick, String pick, Integer pickId, Vec3 pickAt,
                         Vec3 eye, Vec3 look, AABB renderedOpponent) {}

    private static Frame latestFrame;

    private ClientDuelTraceView() {}

    public static void clear() { latestFrame = null; }

    public static void render(Minecraft game, float partialTick) {
        Entity camera = game.getCameraEntity();
        if (camera == null) return;
        UUID opponent = ClientDuelTrace.opponent();
        Entity target = opponent(game, opponent);
        AABB renderedBox = null;
        if (target != null) {
            // LevelRenderer.renderEntity interpolates xOld/yOld/zOld for the rendered entity.
            renderedBox = target.getBoundingBox().move(
                    Mth.lerp(partialTick, target.xOld, target.getX()) - target.getX(),
                    Mth.lerp(partialTick, target.yOld, target.getY()) - target.getY(),
                    Mth.lerp(partialTick, target.zOld, target.getZ()) - target.getZ());
        }
        HitResult pick = game.hitResult;
        latestFrame = new Frame(System.nanoTime(), opponent, partialTick, pickType(pick), pickId(pick),
                pick == null ? null : pick.getLocation(),
                camera.getEyePosition(partialTick), camera.getViewVector(partialTick), renderedBox);
    }

    public static void attack(Minecraft game, int missTime, String gate) {
        var player = game.player;
        Entity camera = game.getCameraEntity();
        if (player == null || camera == null) return;
        UUID opponent = ClientDuelTrace.opponent();
        Entity target = opponent(game, opponent);
        HitResult tickPick = game.hitResult;
        Frame frame = latestFrame;
        long frameAge = frame == null ? -1 : (System.nanoTime() - frame.capturedAt()) / 1_000L;
        if (frame != null && (frameAge > 250_000L || !Objects.equals(opponent, frame.opponent()))) {
            frame = null;
        }
        Vec3 eye = camera.getEyePosition(1.0F);
        ClientDuelTrace.event("attack_start", "gate", gate, "missTime", missTime,
                "usingItem", player.isUsingItem(), "onGround", player.onGround(), "sprinting", player.isSprinting(),
                "selectedSlot", player.getInventory().selected, "weapon", player.getMainHandItem().getItem().toString(),
                "firstPerson", game.options.getCameraType().isFirstPerson(), "reach", player.entityInteractionRange(),
                "eye", vector(eye), "look", vector(camera.getViewVector(1.0F)),
                "opponentBox", target == null ? null : box(target.getBoundingBox()),
                "opponentDistanceToBox", target == null ? null : Math.sqrt(target.getBoundingBox().distanceToSqr(eye)),
                "tickPick", pickType(tickPick), "tickPickId", pickId(tickPick), "tickPickAt", pickPoint(tickPick),
                "frameAgeUs", frame == null ? null : frameAge, "framePartialTick", frame == null ? null : frame.partialTick(),
                "framePick", frame == null ? null : frame.pick(), "framePickId", frame == null ? null : frame.pickId(),
                "framePickAt", frame == null || frame.pickAt() == null ? null : vector(frame.pickAt()),
                "frameEye", frame == null ? null : vector(frame.eye()),
                "frameLook", frame == null ? null : vector(frame.look()),
                "frameOpponentBox", frame == null || frame.renderedOpponent() == null ? null : box(frame.renderedOpponent()));
    }

    private static Entity opponent(Minecraft game, UUID id) {
        if (id == null || game.level == null) return null;
        for (var candidate : game.level.players()) {
            if (id.equals(candidate.getUUID())) return candidate;
        }
        return null;
    }

    private static String pickType(HitResult pick) { return pick == null ? "NONE" : pick.getType().name(); }
    private static Integer pickId(HitResult pick) { return pick instanceof EntityHitResult hit ? hit.getEntity().getId() : null; }
    private static double[] pickPoint(HitResult pick) { return pick == null ? null : vector(pick.getLocation()); }
    private static double[] vector(Vec3 v) { return new double[]{v.x, v.y, v.z}; }
    private static double[] box(AABB b) { return new double[]{b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ}; }
}
