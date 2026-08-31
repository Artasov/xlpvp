package com.xlpvp.client;

import com.xlpvp.Core;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Core.MODID, value = Dist.CLIENT)
public final class AttackSoundHandler {

    private static final String VANILLA_ATTACK_PREFIX = "minecraft:entity.player.attack.";
    private static final String VANILLA_ATTACK_NODAMAGE = VANILLA_ATTACK_PREFIX + "nodamage";
    private static final String VANILLA_PLAYER_HURT = "minecraft:entity.player.hurt";
    private static final long OLD_PVP_HIT_DELAY_NANOS = 10L * 50_000_000L;
    private static final long LOCAL_ATTACK_SOUND_WINDOW_NANOS = 100_000_000L;
    private static final long SERVER_HURT_SUPPRESS_WINDOW_NANOS = 600_000_000L;
    private static final double SERVER_HURT_SUPPRESS_DISTANCE_SQ = 2.25D;
    private static final Deque<PredictedHurtSound> LOCAL_ATTACKS = new ArrayDeque<>();
    private static final Deque<PredictedHurtSound> LOCAL_HURT_SOUNDS = new ArrayDeque<>();
    private static long lastLocalAttackNanos = Long.MIN_VALUE;

    private AttackSoundHandler() {
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }

        long now = System.nanoTime();
        if (lastLocalAttackNanos != Long.MIN_VALUE && now - lastLocalAttackNanos < OLD_PVP_HIT_DELAY_NANOS) {
            return;
        }

        lastLocalAttackNanos = now;
        LOCAL_ATTACKS.addLast(new PredictedHurtSound(target.getX(), target.getY(), target.getZ(), now));
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance originalSound = event.getOriginalSound();
        if (originalSound == null) {
            return;
        }

        long now = System.nanoTime();
        pruneExpired(LOCAL_ATTACKS, now, LOCAL_ATTACK_SOUND_WINDOW_NANOS);
        pruneExpired(LOCAL_HURT_SOUNDS, now, SERVER_HURT_SUPPRESS_WINDOW_NANOS);

        String soundId = originalSound.getLocation().toString();
        if (soundId.startsWith(VANILLA_ATTACK_PREFIX)) {
            PredictedHurtSound localAttack = pollLocalAttack(now);
            if (!soundId.equals(VANILLA_ATTACK_NODAMAGE) && localAttack != null) {
                LOCAL_HURT_SOUNDS.addLast(localAttack.withCreatedAt(now));
                event.setSound(new SimpleSoundInstance(
                        SoundEvents.PLAYER_HURT,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F,
                        SoundInstance.createUnseededRandom(),
                        localAttack.x(),
                        localAttack.y(),
                        localAttack.z()));
                return;
            }

            event.setSound(null);
            return;
        }

        if (soundId.equals(VANILLA_PLAYER_HURT) && removeMatchingLocalHurt(originalSound, now)) {
            event.setSound(null);
        }
    }

    private static PredictedHurtSound pollLocalAttack(long now) {
        while (!LOCAL_ATTACKS.isEmpty()) {
            PredictedHurtSound localAttack = LOCAL_ATTACKS.removeFirst();
            if (!localAttack.isExpired(now, LOCAL_ATTACK_SOUND_WINDOW_NANOS)) {
                return localAttack;
            }
        }

        return null;
    }

    private static boolean removeMatchingLocalHurt(SoundInstance sound, long now) {
        Iterator<PredictedHurtSound> iterator = LOCAL_HURT_SOUNDS.iterator();
        while (iterator.hasNext()) {
            PredictedHurtSound localSound = iterator.next();
            if (localSound.isExpired(now, SERVER_HURT_SUPPRESS_WINDOW_NANOS)) {
                iterator.remove();
                continue;
            }

            if (localSound.isCloseTo(sound)) {
                iterator.remove();
                return true;
            }
        }

        return false;
    }

    private static void pruneExpired(Deque<PredictedHurtSound> sounds, long now, long maxAgeNanos) {
        while (!sounds.isEmpty() && sounds.peekFirst().isExpired(now, maxAgeNanos)) {
            sounds.removeFirst();
        }
    }

    private record PredictedHurtSound(double x, double y, double z, long createdAtNanos) {

        private PredictedHurtSound withCreatedAt(long now) {
            return new PredictedHurtSound(x, y, z, now);
        }

        private boolean isExpired(long now, long maxAgeNanos) {
            return now - createdAtNanos > maxAgeNanos;
        }

        private boolean isCloseTo(SoundInstance sound) {
            double dx = x - sound.getX();
            double dy = y - sound.getY();
            double dz = z - sound.getZ();
            return dx * dx + dy * dy + dz * dz <= SERVER_HURT_SUPPRESS_DISTANCE_SQ;
        }
    }
}
