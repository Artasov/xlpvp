package com.xlpvp.main;

import com.xlpvp.Core;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@EventBusSubscriber(modid = Core.MODID)
public final class ClassicPvpHandler {

    /* ---------- 1. мгновенный удар ---------- */

    private static final double OLD_PVP_ATTACK_SPEED = 20.0D;
    private static final double OLD_PVP_REACH_EXTRA = 0.0D;
    private static final long OLD_PVP_HIT_LOCK_TICKS = 10L;

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        Player player = e.getEntity();
        clearCombatState(player.getUUID());
        applyFastAttack(player);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        clearCombatState(e.getOriginal().getUUID());
        clearCombatState(e.getEntity().getUUID());
        applyFastAttack(e.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        clearCombatState(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        Player player = e.getEntity();
        clearCombatState(player.getUUID());
        player.stopUsingItem();
    }

    private static void clearCombatState(UUID id) {
        READY.remove(id);
        PREV_SPRINT.remove(id);
        ATTACK_KB.entrySet().removeIf(entry ->
            entry.getKey().equals(id) || entry.getValue().attackerId().equals(id)
        );
        SUPPRESS_NEXT_KB.remove(id);
        LAST_PVP_HIT.remove(id);
    }

    private static void applyFastAttack(Player p) {
        AttributeInstance attr = p.getAttribute(Attributes.ATTACK_SPEED);
        if (attr != null && attr.getBaseValue() < OLD_PVP_ATTACK_SPEED) {
            attr.setBaseValue(OLD_PVP_ATTACK_SPEED);
        }
    }

    /* ---------- old pvp 1.7.10 с W-/S-tap ---------- */

    private static final float KB_NORMAL = 0.408F;
    private static final float KB_SPRINT = 0.816F;


    private static final Map<UUID, Boolean> READY = new ConcurrentHashMap<>();

    private static final Map<UUID, Boolean> PREV_SPRINT = new ConcurrentHashMap<>();

    private static final Map<UUID, AttackKnockback> ATTACK_KB = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> SUPPRESS_NEXT_KB = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> LAST_PVP_HIT = new ConcurrentHashMap<>();

    private record AttackKnockback(UUID attackerId, boolean strong, double x, double z, long gameTime) {
    }

    /**
     * Выдаёт коэфф. KB, компенсируя {@code generic.knockback_resistance}.
     */
    public static float adjustKnockback(float desired, LivingEntity ent) {
        double resist = Math.max(0.0, ent.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (resist >= 1.0) return 0.0F;                // полное сопротивление — остаётся «0»
        return (float) (desired / (1.0 - resist));     // масштабируем вверх
    }

    @SubscribeEvent
    public static void onTickPre(PlayerTickEvent.Pre e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) return;

        UUID id = p.getUUID();
        boolean s = p.isSprinting();
        boolean prev = PREV_SPRINT.getOrDefault(id, false);

        if (s && !prev) READY.put(id, true);     // старт спринта
        else if (!s) READY.put(id, false);    // спринт прерван

        PREV_SPRINT.put(id, s);
    }

    public static boolean takeReady(Player p) {
        UUID id = p.getUUID();
        if (!p.isSprinting()) {
            READY.put(id, false);
            return false;
        }

        boolean ready = READY.getOrDefault(id, false);
        if (!ready && !PREV_SPRINT.getOrDefault(id, false)) {
            ready = true;
            PREV_SPRINT.put(id, true);
        }

        READY.put(id, false);
        return ready;
    }

    public static boolean canReachClassicTarget(Player player, LivingEntity target) {
        return player.canInteractWithEntity(target.getBoundingBox(), OLD_PVP_REACH_EXTRA);
    }

    public static boolean isClassicPvpHitLocked(Player target) {
        UUID targetId = target.getUUID();
        Long lastHitTick = LAST_PVP_HIT.get(targetId);
        if (lastHitTick == null) {
            return false;
        }

        long serverTick = getServerTick(target);
        if (serverTick == Long.MIN_VALUE) {
            LAST_PVP_HIT.remove(targetId, lastHitTick);
            return false;
        }

        long elapsedTicks = serverTick - lastHitTick;
        if (elapsedTicks >= 0L && elapsedTicks < OLD_PVP_HIT_LOCK_TICKS) {
            return true;
        }

        LAST_PVP_HIT.remove(targetId, lastHitTick);
        return false;
    }

    public static void markClassicPvpHit(Player target) {
        long serverTick = getServerTick(target);
        if (serverTick != Long.MIN_VALUE) {
            LAST_PVP_HIT.put(target.getUUID(), serverTick);
        }
    }

    private static long getServerTick(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return Long.MIN_VALUE;
        }

        return Integer.toUnsignedLong(serverLevel.getServer().getTickCount());
    }

    public static boolean hasQueuedSprintKnockback(Player attacker, Player target) {
        AttackKnockback knockback = ATTACK_KB.get(target.getUUID());
        return knockback != null
            && knockback.strong()
            && knockback.attackerId().equals(attacker.getUUID())
            && knockback.gameTime() == attacker.level().getGameTime();
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        Player attacker = e.getEntity();
        if (attacker.level().isClientSide) return;
        if (attacker.isUsingItem()) {
            e.setCanceled(true);
            return;
        }
        if (!(e.getTarget() instanceof Player target)) return;
        if (isClassicPvpHitLocked(target)) {
            e.setCanceled(true);
            return;
        }
        boolean strong = attacker.isSprinting() && takeReady(attacker);
        double yaw = Math.toRadians(attacker.getYRot());
        ATTACK_KB.put(
            target.getUUID(),
            new AttackKnockback(attacker.getUUID(), strong, Math.sin(yaw), -Math.cos(yaw), attacker.level().getGameTime())
        );
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent e) {
        LivingEntity victim = e.getEntity();
        UUID victimId = victim.getUUID();
        long gameTime = victim.level().getGameTime();

        Long suppressGameTime = SUPPRESS_NEXT_KB.remove(victimId);
        if (suppressGameTime != null && suppressGameTime == gameTime) {
            e.setCanceled(true);
            return;
        }

        AttackKnockback knockback = ATTACK_KB.remove(victimId);
        if (knockback == null || knockback.gameTime() != gameTime) {
            return;
        }

        e.setCanceled(true);

        float desired = knockback.strong() ? KB_SPRINT : KB_NORMAL;
        if (victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0D) {
            return;
        }

        double ratioX = knockback.strong() ? knockback.x() : e.getOriginalRatioX();
        double ratioZ = knockback.strong() ? knockback.z() : e.getOriginalRatioZ();
        while (ratioX * ratioX + ratioZ * ratioZ < 1.0E-5F) {
            ratioX = (Math.random() - Math.random()) * 0.01D;
            ratioZ = (Math.random() - Math.random()) * 0.01D;
        }

        victim.hasImpulse = true;
        Vec3 movement = victim.getDeltaMovement();
        Vec3 knockbackVector = new Vec3(ratioX, 0.0D, ratioZ).normalize().scale(desired);
        victim.setDeltaMovement(
            movement.x / 2.0D - knockbackVector.x,
            Math.min(0.4D, movement.y / 2.0D + desired),
            movement.z / 2.0D - knockbackVector.z
        );
        victim.hurtMarked = true;
        if (victim instanceof Player target) {
            markClassicPvpHit(target);
        }
        SUPPRESS_NEXT_KB.put(victimId, gameTime);
    }
}
