package com.xlpvp.client;

import com.xlpvp.Core;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Core.MODID, value = Dist.CLIENT)
public final class AttackSoundHandler {

    private static final String VANILLA_ATTACK_PREFIX = "minecraft:entity.player.attack.";

    private AttackSoundHandler() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getOriginalSound() == null) {
            return;
        }
        String soundId = event.getOriginalSound().getLocation().toString();
        if (soundId.startsWith(VANILLA_ATTACK_PREFIX)) {
            event.setSound(null);
        }
    }
}
