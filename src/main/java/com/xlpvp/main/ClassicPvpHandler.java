package com.xlpvp.main;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.xlpvp.Core;
import com.xlpvp.trace.DuelTrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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

import java.util.Map;
import java.util.Set;
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
        USED_SPRINT.remove(id);
        LAST_PVP_HIT.remove(id);
    }

    private static void applyFastAttack(Player p) {
        AttributeInstance attr = p.getAttribute(Attributes.ATTACK_SPEED);
        if (attr != null && attr.getBaseValue() < OLD_PVP_ATTACK_SPEED) {
            attr.setBaseValue(OLD_PVP_ATTACK_SPEED);
        }
    }

    /* ---------- old pvp 1.7.10 с W-/S-tap ---------- */

    private static final double BASE_KNOCKBACK = 0.4F;
    private static final double SPRINT_KNOCKBACK_BONUS = 0.4;
    private static final double EXTRA_KNOCKBACK_PER_LEVEL = 0.5;


    private static final Set<UUID> USED_SPRINT = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<AttackKnockback> CURRENT_ATTACK = new ThreadLocal<>();

    private static final Map<UUID, Long> LAST_PVP_HIT = new ConcurrentHashMap<>();

    private static final class AttackKnockback {
        final Player attacker;
        final Player target;
        final boolean strong;
        boolean skipNextKnockback;

        AttackKnockback(Player attacker, Player target) {
            this.attacker = attacker;
            this.target = target;
            this.strong = !attacker.level().isClientSide() && attacker.isSprinting() && !USED_SPRINT.contains(attacker.getUUID());
        }
    }

    /**
     * Выдаёт коэфф. KB, компенсируя {@code generic.knockback_resistance}.
     */
    public static float adjustKnockback(float desired, LivingEntity ent) {
        double resist = Math.max(0.0, ent.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (resist >= 1.0) return 0.0F;                // полное сопротивление — остаётся «0»
        return (float) (desired / (1.0 - resist));     // масштабируем вверх
    }

    public static void onSprintStopped(Player player) {
        boolean wasUsed = USED_SPRINT.remove(player.getUUID());
        if (wasUsed && !player.level().isClientSide() && DuelTrace.active(player)) {
            DuelTrace.event(player, null, "sprint_knockback_rearmed");
        }
    }

    public static void performAttack(Player attacker, Entity target, Operation<Void> original) {
        boolean traced = !attacker.level().isClientSide() && DuelTrace.pair(attacker, target);
        if (traced) {
            DuelTrace.event(attacker, target, "melee_attempt",
                    "distanceToBox", Math.sqrt(target.getBoundingBox().distanceToSqr(attacker.getEyePosition())),
                    "reach", attacker.entityInteractionRange() + OLD_PVP_REACH_EXTRA,
                    "sprinting", attacker.isSprinting(), "sprintUsed", USED_SPRINT.contains(attacker.getUUID()),
                    "usingItem", attacker.isUsingItem(), "weapon", attacker.getMainHandItem().getItem().toString(),
                    "attackStrength", attacker.getAttackStrengthScale(0.5F), "targetInvulnerableTicks", target.invulnerableTime);
        }
        if (target instanceof LivingEntity livingTarget && !canReachClassicTarget(attacker, livingTarget)) {
            if (traced) DuelTrace.event(attacker, target, "melee_rejected", "reason", "reach");
            return;
        }
        AttackKnockback previous = CURRENT_ATTACK.get();
        if (target instanceof Player opponent && !attacker.isUsingItem()) {
            CURRENT_ATTACK.set(new AttackKnockback(attacker, opponent));
        } else if (previous != null) {
            CURRENT_ATTACK.remove();
        }
        try {
            original.call(target);
            if (traced) DuelTrace.event(attacker, target, "melee_end");
        } finally {
            if (previous == null) {
                CURRENT_ATTACK.remove();
            } else {
                CURRENT_ATTACK.set(previous);
            }
        }
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

    static void traceHitLock(Player attacker, Player target, String event) {
        if (DuelTrace.pair(attacker, target)) {
            Long last = LAST_PVP_HIT.get(target.getUUID());
            DuelTrace.event(attacker, target, event, "reason", "hit_lock", "lastHitTick", last,
                    "lockTicks", OLD_PVP_HIT_LOCK_TICKS);
        }
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

    public static boolean isClassicAttack(Player attacker) {
        AttackKnockback attack = CURRENT_ATTACK.get();
        return attack != null && attack.attacker == attacker;
    }

    public static boolean useSprintKnockback(Player attacker) {
        AttackKnockback attack = CURRENT_ATTACK.get();
        return attack != null && attack.attacker == attacker ? attack.strong : attacker.isSprinting();
    }

    public static void onSuccessfulAttack(Player attacker, Entity target, float weaponKnockback) {
        AttackKnockback attack = CURRENT_ATTACK.get();
        if (attack == null || attack.attacker != attacker || attack.target != target || attacker.level().isClientSide()) return;
        // Player.attack can request a second vanilla knockback after the damage impulse.
        attack.skipNextKnockback = true;
        if (attack.strong) {
            USED_SPRINT.add(attacker.getUUID());
        }
        double extra = weaponKnockback * EXTRA_KNOCKBACK_PER_LEVEL + (attack.strong ? SPRINT_KNOCKBACK_BONUS : 0);
        if (DuelTrace.pair(attacker, target)) {
            DuelTrace.event(attacker, target, "melee_accepted", "sprintBonus", attack.strong,
                    "weaponKnockback", weaponKnockback, "extraStrengthRequested", extra);
        }
        if (extra <= 0 || attack.target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0) return;

        double yaw = Math.toRadians(attacker.getYRot());
        attack.target.push(-Math.sin(yaw) * extra, 0.1, Math.cos(yaw) * extra);
        attack.target.hurtMarked = true;
        if (DuelTrace.pair(attacker, target)) {
            traceVelocity(attacker, target, "melee_sprint_or_weapon_impulse", target.getDeltaMovement());
        }
        markClassicPvpHit(attack.target);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        Player attacker = e.getEntity();
        if (attacker.level().isClientSide) return;
        if (attacker.isUsingItem()) {
            if (DuelTrace.pair(attacker, e.getTarget())) {
                DuelTrace.event(attacker, e.getTarget(), "melee_rejected", "reason", "using_item");
            }
            e.setCanceled(true);
            return;
        }
        if (!(e.getTarget() instanceof Player target)) return;
        if (isClassicPvpHitLocked(target)) {
            traceHitLock(attacker, target, "melee_rejected");
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent e) {
        LivingEntity victim = e.getEntity();
        AttackKnockback knockback = CURRENT_ATTACK.get();
        if (knockback == null || knockback.target != victim || victim.level().isClientSide()) {
            return;
        }

        e.setCanceled(true);
        if (knockback.skipNextKnockback) {
            if (DuelTrace.pair(knockback.attacker, victim)) {
                DuelTrace.event(knockback.attacker, victim, "melee_extra_vanilla_impulse_skipped");
            }
            return;
        }
        knockback.skipNextKnockback = true;
        if (victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0D) {
            if (DuelTrace.pair(knockback.attacker, victim)) {
                DuelTrace.event(knockback.attacker, victim, "melee_impulse_resisted");
            }
            return;
        }

        double ratioX = e.getOriginalRatioX();
        double ratioZ = e.getOriginalRatioZ();
        while (ratioX * ratioX + ratioZ * ratioZ < 1.0E-5F) {
            ratioX = (Math.random() - Math.random()) * 0.01D;
            ratioZ = (Math.random() - Math.random()) * 0.01D;
        }

        victim.hasImpulse = true;
        Vec3 movement = victim.getDeltaMovement();
        Vec3 knockbackVector = new Vec3(ratioX, 0.0D, ratioZ).normalize().scale(BASE_KNOCKBACK);
        victim.setDeltaMovement(
            movement.x / 2.0D - knockbackVector.x,
            Math.min(BASE_KNOCKBACK, movement.y / 2.0D + BASE_KNOCKBACK),
            movement.z / 2.0D - knockbackVector.z
        );
        victim.hurtMarked = true;
        if (DuelTrace.pair(knockback.attacker, victim)) {
            traceVelocity(knockback.attacker, victim, "melee_base_impulse", victim.getDeltaMovement());
        }
        markClassicPvpHit(knockback.target);
    }

    private static void traceVelocity(Player attacker, Entity target, String kind, Vec3 motion) {
        DuelTrace.event(attacker, target, kind, "vx", motion.x, "vy", motion.y, "vz", motion.z);
    }
}
