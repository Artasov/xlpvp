package com.xlpvp.client;

import com.xlpvp.Core;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Делает спринт «по умолчанию»: как только игрок хоть как‑то движется вперёд/вбок,
 * он мгновенно переходит в состояние бега.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Core.MODID, value = Dist.CLIENT)
public final class AutoSprintHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post evt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if ((mc.player.input.forwardImpulse != 0 || mc.player.input.leftImpulse != 0)
                && !mc.player.isShiftKeyDown()) {
            mc.player.setSprinting(true);
        }
    }
}
