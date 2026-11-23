package com.xlpvp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.UUID;

/** Keeps a recently drawn aim direction with the press that queued the attack. */
public final class ClassicPvpInput {
    private static final long MAX_FRAME_AGE_NANOS = 100_000_000L;
    private static final int MAX_PENDING_CLICKS = 32;
    private static final double MAX_FRAME_MOVEMENT_SQR = 4.0 * 4.0;
    private static final double MAX_EYE_MOVEMENT_SQR = 0.40 * 0.40;
    private static final double MIN_LOOK_DOT = Math.cos(Math.toRadians(8.0));
    private static final double RAY_END_EPSILON = 1.0e-4;
    private static final ArrayDeque<Click> pending = new ArrayDeque<>();
    private static AimFrame lastFrame;

    private ClassicPvpInput() {}

    private record AimFrame(long at, UUID player, Vec3 eye, int targetId, UUID target, Vec3 targetPos, Vec3 direction) {}
    public record Click(long at, AimFrame aim) {}

    public static void render(Minecraft game, float partialTick) {
        LocalPlayer player = game.player;
        if (canAttack(player) && game.getCameraEntity() == player && game.options.getCameraType().isFirstPerson()
                && game.hitResult instanceof EntityHitResult hit && canHitPlayer(player, hit)) {
            Entity target = hit.getEntity();
            lastFrame = new AimFrame(System.nanoTime(), player.getUUID(), player.getEyePosition(partialTick),
                    target.getId(), target.getUUID(), target.position(), player.getViewVector(partialTick));
        } else {
            lastFrame = null;
        }
    }

    public static void queueClick() {
        long now = System.nanoTime();
        if (pending.size() == MAX_PENDING_CLICKS) pending.removeFirst();
        AimFrame frame = lastFrame;
        pending.addLast(new Click(now, frame != null && now - frame.at() <= MAX_FRAME_AGE_NANOS ? frame : null));
    }

    public static Click consumeClick() {
        return pending.pollFirst();
    }

    public static void clearClicks() {
        pending.clear();
    }

    public static boolean canIgnoreMissTime(Minecraft game) {
        return canAttack(game.player) && game.hitResult instanceof EntityHitResult hit && canHitPlayer(game.player, hit);
    }

    public static EntityHitResult recoverPlayerPick(Minecraft game, Click click) {
        if (click == null || click.aim() == null || System.nanoTime() - click.at() > MAX_FRAME_AGE_NANOS
                || game.hitResult == null || game.hitResult.getType() != HitResult.Type.MISS
                || game.screen != null || game.getOverlay() != null || !game.options.getCameraType().isFirstPerson()) return null;
        LocalPlayer player = game.player;
        AimFrame frame = click.aim();
        if (!canAttack(player) || game.getCameraEntity() != player || !frame.player().equals(player.getUUID())
                || System.nanoTime() - frame.at() > MAX_FRAME_AGE_NANOS) return null;
        Vec3 eye = player.getEyePosition();
        if (eye.distanceToSqr(frame.eye()) > MAX_EYE_MOVEMENT_SQR
                || player.getViewVector(1.0F).dot(frame.direction()) < MIN_LOOK_DOT) return null;
        Entity target = player.level().getEntity(frame.targetId());
        if (!isAttackablePlayer(target) || !frame.target().equals(target.getUUID())
                || target.position().distanceToSqr(frame.targetPos()) >= MAX_FRAME_MOVEMENT_SQR) return null;

        double reach = player.entityInteractionRange();
        // Resolve the click against the live box from the drawn eye. Then verify that
        // the same point remains reachable and unobstructed from the current eye.
        var box = target.getBoundingBox();
        Vec3 point = box.contains(frame.eye()) ? frame.eye()
                : box.clip(frame.eye(), frame.eye().add(frame.direction().scale(reach))).orElse(null);
        if (point == null) return null;
        Vec3 toward = point.subtract(eye);
        double distance = toward.length();
        if (distance >= reach) return null;
        Vec3 ray = distance == 0.0 ? frame.direction().scale(RAY_END_EPSILON)
                : toward.scale(Math.min(reach, distance + RAY_END_EPSILON) / distance);
        HitResult block = player.level().clip(new ClipContext(eye, eye.add(ray), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        double limit = block.getType() == HitResult.Type.MISS ? reach * reach : block.getLocation().distanceToSqr(eye);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, block.getLocation(),
                player.getBoundingBox().expandTowards(ray).inflate(1.0),
                entity -> !entity.isSpectator() && entity.isPickable(), limit);
        return hit != null && hit.getEntity() == target && hit.getLocation().distanceToSqr(eye) < limit
                && canHitPlayer(player, hit) ? hit : null;
    }

    private static boolean canAttack(LocalPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator() && !player.isHandsBusy() && !player.isUsingItem();
    }

    private static boolean canHitPlayer(LocalPlayer player, EntityHitResult hit) {
        Entity target = hit.getEntity();
        return isAttackablePlayer(target) && player.canInteractWithEntity(target.getBoundingBox(), 0.0);
    }

    private static boolean isAttackablePlayer(Entity target) {
        return target instanceof Player player && player.isAlive() && !player.isSpectator()
                && player.isAttackable() && player.isPickable() && !player.isRemoved();
    }
}
