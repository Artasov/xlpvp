package com.xlpvp.client;

import com.xlpvp.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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

    private static final int RETRY_DELAY_TICKS = 8;

    private static boolean wasMovingForward;
    private static boolean requestedSprint;
    private static int retryDelayTicks;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post evt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LocalPlayer player = mc.player;
        boolean movingForward = player.input.forwardImpulse > 0.0F;
        boolean justStartedMovingForward = movingForward && !wasMovingForward;
        wasMovingForward = movingForward;

        if (!movingForward) {
            requestedSprint = false;
            retryDelayTicks = 0;
            return;
        }

        if (blocksSprint(player)) {
            requestedSprint = false;
            retryDelayTicks = RETRY_DELAY_TICKS;
            return;
        }

        if (requestedSprint && !player.isSprinting()) {
            requestedSprint = false;
            retryDelayTicks = RETRY_DELAY_TICKS;
        }

        if (retryDelayTicks > 0 && !justStartedMovingForward) {
            retryDelayTicks--;
            return;
        }

        if (!player.isSprinting()) {
            player.setSprinting(true);
            requestedSprint = true;
        }
    }

    private static boolean blocksSprint(LocalPlayer player) {
        return player.isShiftKeyDown()
                || player.horizontalCollision
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || player.isFallFlying()
                || player.isPassenger()
                || (!player.getAbilities().mayfly && player.getFoodData().getFoodLevel() <= 6);
    }

    private AutoSprintHandler() {
    }
}
