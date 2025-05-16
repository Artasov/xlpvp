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

        /* ---------- 1. sweep-sound + red flash (no damage) ---------- */
        owner.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (target instanceof LivingEntity living) {
            living.hurtTime = 10;
            living.hurtDuration = 10;
            living.hurtMarked = true;
        }

        /* ---------- 2. knock-back (1.7-style) ----------------------- */
        boolean strong = owner.isSprinting() && ClassicPvpHandler.takeReady(owner);
        float kb = strong ? KB_SPRINT : KB_NORMAL;

        double yawRad = Math.toRadians(owner.getYRot());
        double xRatio = Math.sin(yawRad);   // correct sign
        double zRatio = -Math.cos(yawRad);   // correct sign
        double vDelta = 0.06D;
        if (target instanceof LivingEntity livingEntity) {
            livingEntity.knockback(kb, xRatio, zRatio);
            livingEntity.setDeltaMovement(livingEntity.getDeltaMovement().add(0.0D, vDelta, 0.0D));
            livingEntity.hurtMarked = true;
        } else {
            // fallback for non-living entities
            target.setDeltaMovement(
                    target.getDeltaMovement().add(xRatio * kb, vDelta, zRatio * kb));
            target.hurtMarked = true;
        }
    }
}
