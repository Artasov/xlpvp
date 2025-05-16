package com.xlpvp.main;

import com.xlpvp.Core;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

@EventBusSubscriber(modid = Core.MODID)
public final class FishingRodKnockbackHandler {

    private static final float KB_NORMAL = 0.4F;
    private static final float KB_SPRINT = 0.8F;

    @SubscribeEvent
    public static void onBobberImpact(ProjectileImpactEvent evt) {
        Entity proj = evt.getEntity();
        if (!(proj instanceof FishingHook hook)) return;

        HitResult hit = evt.getRayTraceResult();
        if (!(hit instanceof EntityHitResult eh)) return;

        Entity target = eh.getEntity();
        Player owner = hook.getOwner() instanceof Player p ? p : null;
        if (owner == null || !target.isAttackable()) return;

        /* ---------- 1. звук + «красный флэш» без урона ---------- */
        owner.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (target instanceof LivingEntity living) {
            // «ручной» флэш, 10 тиков как в vanilla-ударе
            living.hurtTime = 10;
            living.hurtDuration = 10;
            living.hurtMarked = true;
        }

        /* ---------- 2. классический нокаут ---------- */
        boolean strong = owner.isSprinting() && ClassicPvpHandler.takeReady(owner);
        float kb = strong ? KB_SPRINT : KB_NORMAL;

        double yaw = Math.toRadians(owner.getYRot());
        double xRatio = -Math.sin(yaw);
        double zRatio = Math.cos(yaw);
        double dx = xRatio * kb;
        double dz = zRatio * kb;

        if (target instanceof LivingEntity livingEntity) {
            // используем встроенный метод knockback для нормального отбрасывания
            livingEntity.knockback(kb, xRatio, zRatio);
            // добавляем небольшую вертикальную составляющую, как в vanilla-ударе
            livingEntity.setDeltaMovement(
                    livingEntity.getDeltaMovement().add(0.0D, 0.1D, 0.0D)
            );
            livingEntity.hurtMarked = true;
        } else {
            // для прочих сущностей — fallback на старый вариант
            target.setDeltaMovement(target.getDeltaMovement().add(dx, 0.1D, dz));
            target.hurtMarked = true;
        }
    }
}
