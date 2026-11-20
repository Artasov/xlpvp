package com.xlpvp.client;

import com.xlpvp.Core;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(modid = Core.MODID, value = Dist.CLIENT)
public final class AttackSoundHandler {
    private AttackSoundHandler() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        ResourceLocation id = event.getOriginalSound().getLocation();
        // An accepted hit already plays the victim's own hurt sound.
        if (id.getNamespace().equals("minecraft") && id.getPath().startsWith("entity.player.attack.")) {
            event.setSound(null);
        }
    }
}
