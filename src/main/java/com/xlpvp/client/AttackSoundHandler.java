package com.xlpvp.client;

import com.xlpvp.Core;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Core.MODID, value = Dist.CLIENT)
public final class AttackSoundHandler {

    private static final String VANILLA_ATTACK_PREFIX = "minecraft:entity.player.attack.";
    private static final String VANILLA_ATTACK_NODAMAGE = VANILLA_ATTACK_PREFIX + "nodamage";

    private AttackSoundHandler() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getOriginalSound() == null) {
            return;
        }
        String soundId = event.getOriginalSound().getLocation().toString();
        if (soundId.startsWith(VANILLA_ATTACK_PREFIX)) {
            if (soundId.equals(VANILLA_ATTACK_NODAMAGE)) {
                event.setSound(null);
                return;
            }

            SoundInstance originalSound = event.getOriginalSound();
            event.setSound(new SimpleSoundInstance(
                    SoundEvents.PLAYER_HURT,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F,
                    SoundInstance.createUnseededRandom(),
                    originalSound.getX(),
                    originalSound.getY(),
                    originalSound.getZ()));
        }
    }
}
