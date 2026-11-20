package com.xlpvp.main;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.xlpvp.Core;
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

    private static final float KB_NORMAL = 0.408F;
    private static final float KB_SPRINT = 0.816F;


    private static final Set<UUID> USED_SPRINT = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<AttackKnockback> CURRENT_ATTACK = new ThreadLocal<>();

    private static final Map<UUID, Long> LAST_PVP_HIT = new ConcurrentHashMap<>();

    private static final class AttackKnockback {
        final Player attacker;
        final Player target;
        final boolean strong;
        boolean applied;

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
        USED_SPRINT.remove(player.getUUID());
    }

    public static void performAttack(Player attacker, Entity target, Operation<Void> original) {
        if (target instanceof LivingEntity livingTarget && !canReachClassicTarget(attacker, livingTarget)) {
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

    public static void onSuccessfulAttack(Player attacker, Entity target) {
        AttackKnockback attack = CURRENT_ATTACK.get();
        if (attack == null || attack.attacker != attacker || attack.target != target || attacker.level().isClientSide()) return;
        if (attack.strong) {
            USED_SPRINT.add(attacker.getUUID());
        }
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
        if (knockback.applied) return; // The same Player.attack can request both damage and sprint knockback.
        knockback.applied = true;
        float desired = knockback.strong ? KB_SPRINT : KB_NORMAL;
        if (victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0D) {
            return;
        }

        double yaw = Math.toRadians(knockback.attacker.getYRot());
        double ratioX = knockback.strong ? Math.sin(yaw) : e.getOriginalRatioX();
        double ratioZ = knockback.strong ? -Math.cos(yaw) : e.getOriginalRatioZ();
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
        markClassicPvpHit(knockback.target);
    }
}
