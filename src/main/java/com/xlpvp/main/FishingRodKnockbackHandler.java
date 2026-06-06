package com.xlpvp.main;

import com.xlpvp.Core;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

@EventBusSubscriber(modid = Core.MODID)
public final class FishingRodKnockbackHandler {

    private static final float ROD_KB = 0.408F;
    private static final double NON_LIVING_VERTICAL_DELTA = 0.1D;

    @SubscribeEvent
    public static void onBobberImpact(ProjectileImpactEvent evt) {
        Entity proj = evt.getEntity();
        if (!(proj instanceof FishingHook hook)) return;

        HitResult hit = evt.getRayTraceResult();
        if (!(hit instanceof EntityHitResult eh)) return;

        Entity target = eh.getEntity();
        Player owner = hook.getOwner() instanceof Player p ? p : null;
        if (owner == null || !target.isAttackable()) return;
        if (owner.level().isClientSide()) return;
        if (target instanceof Player targetPlayer && ClassicPvpHandler.isClassicPvpHitLocked(targetPlayer)) {
            return;
        }

        owner.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (target instanceof LivingEntity living) {
            living.hurtTime = 10;
            living.hurtDuration = 10;
            living.hurtMarked = true;
        }

        double yawRad = Math.toRadians(owner.getYRot());
        double xRatio = Math.sin(yawRad);   // correct sign
        double zRatio = -Math.cos(yawRad);   // correct sign
        Vec3 oldMovement = target.getDeltaMovement();
        if (target instanceof LivingEntity livingEntity) {
            applyRodKnockback(livingEntity, xRatio, zRatio);
            livingEntity.hurtMarked = true;
        } else {
            // fallback for non-living entities
            target.setDeltaMovement(
                    target.getDeltaMovement().add(-xRatio * ROD_KB, NON_LIVING_VERTICAL_DELTA, -zRatio * ROD_KB));
            target.hurtMarked = true;
        }
        sendMotionNow(target, oldMovement);
        if (target instanceof Player targetPlayer) {
            ClassicPvpHandler.markClassicPvpHit(targetPlayer);
        }
    }

    private static void applyRodKnockback(LivingEntity livingEntity, double xRatio, double zRatio) {
        double strength = ClassicPvpHandler.adjustKnockback(ROD_KB, livingEntity);
        strength *= 1.0D - Math.max(0.0D, livingEntity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (strength <= 0.0D) {
            return;
        }

        livingEntity.hasImpulse = true;
        Vec3 movement = livingEntity.getDeltaMovement();
        while (xRatio * xRatio + zRatio * zRatio < 1.0E-5F) {
            xRatio = (Math.random() - Math.random()) * 0.01D;
            zRatio = (Math.random() - Math.random()) * 0.01D;
        }

        Vec3 knockback = new Vec3(xRatio, 0.0D, zRatio).normalize().scale(strength);
        livingEntity.setDeltaMovement(
                movement.x / 2.0D - knockback.x,
                livingEntity.onGround() ? Math.min(0.4D, movement.y / 2.0D + strength) : movement.y,
                movement.z / 2.0D - knockback.z);
    }

    private static void sendMotionNow(Entity target, Vec3 oldMovement) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(target));
            target.hurtMarked = false;
            target.setDeltaMovement(oldMovement);
            return;
        }

        serverLevel.getChunkSource().broadcastAndSend(target, new ClientboundSetEntityMotionPacket(target));
        target.hurtMarked = false;
    }
}
