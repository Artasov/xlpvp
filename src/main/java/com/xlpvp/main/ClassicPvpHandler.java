package com.xlpvp.main;

import com.xlpvp.Core;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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
    private static final long OLD_PVP_HIT_DELAY_TICKS = 10L;

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        applyFastAttack(e.getEntity());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        applyFastAttack(e.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        READY.remove(id);
        PREV_SPRINT.remove(id);
        EXTRA_KB.remove(id);
        SUPPRESS_NEXT_KB.remove(id);
        LAST_ACCEPTED_ATTACK.remove(id);
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

    private static final Map<UUID, Vec> EXTRA_KB = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> SUPPRESS_NEXT_KB = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> LAST_ACCEPTED_ATTACK = new ConcurrentHashMap<>();

    private record Vec(double x, double z, long gameTime) {
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

    public static boolean acceptClassicAttack(Player player) {
        if (player.level().isClientSide) {
            return true;
        }

        UUID playerId = player.getUUID();
        long gameTime = player.level().getGameTime();
        Long lastHit = LAST_ACCEPTED_ATTACK.get(playerId);
        if (lastHit != null && gameTime >= lastHit && gameTime - lastHit < OLD_PVP_HIT_DELAY_TICKS) {
            return false;
        }

        LAST_ACCEPTED_ATTACK.put(playerId, gameTime);
        return true;
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        Player attacker = e.getEntity();
        if (attacker.level().isClientSide) return;
        if (attacker.isUsingItem()) {
            e.setCanceled(true);
            return;
        }
        if (!(e.getTarget() instanceof LivingEntity target)) return;
        boolean strong = attacker.isSprinting() && takeReady(attacker);
        if (!strong) return;                    // обычный удар — KB не трогаем
        double yaw = Math.toRadians(attacker.getYRot());
        EXTRA_KB.put(target.getUUID(), new Vec(Math.sin(yaw), -Math.cos(yaw), attacker.level().getGameTime()));
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

        Vec dir = EXTRA_KB.remove(victimId);
        if (dir != null && dir.gameTime() != gameTime) {
            dir = null;
        }

        float desired = (dir == null ? KB_NORMAL : KB_SPRINT);
        e.setStrength(adjustKnockback(desired, victim));

        if (dir != null) {
            e.setRatioX(dir.x);
            e.setRatioZ(dir.z);
            SUPPRESS_NEXT_KB.put(victimId, gameTime);
        }
    }
}
