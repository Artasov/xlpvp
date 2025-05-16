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

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        applyFastAttack(e.getEntity());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        applyFastAttack(e.getEntity());
    }

    private static void applyFastAttack(Player p) {
        AttributeInstance attr = p.getAttribute(Attributes.ATTACK_SPEED);
        if (attr != null && attr.getBaseValue() < OLD_PVP_ATTACK_SPEED) {
            attr.setBaseValue(OLD_PVP_ATTACK_SPEED);
        }
    }

    /* ---------- old pvp 1.7.10 с W-/S-tap ---------- */

    private static final float KB_NORMAL = 0.4F;
    private static final float KB_SPRINT = 0.8F;


    private static final Map<UUID, Boolean> READY = new ConcurrentHashMap<>();

    private static final Map<UUID, Boolean> PREV_SPRINT = new ConcurrentHashMap<>();

    private static final Map<UUID, Vec> EXTRA_KB = new ConcurrentHashMap<>();

    private record Vec(double x, double z) {
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
        READY.compute(p.getUUID(), (k, v) -> v != null && v);
        return !READY.get(p.getUUID());
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
        EXTRA_KB.put(target.getUUID(), new Vec(-Math.sin(yaw), Math.cos(yaw)));
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent e) {
        Vec dir = EXTRA_KB.remove(e.getEntity().getUUID());
        if (dir == null) {                      // усиления нет
            e.setStrength(KB_NORMAL);
            return;
        }
        e.setStrength(KB_SPRINT);
        e.setRatioX(dir.x);
        e.setRatioZ(dir.z);
    }
}
