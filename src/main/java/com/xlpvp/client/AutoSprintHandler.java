package com.xlpvp.client;

import com.xlpvp.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Core.MODID, value = Dist.CLIENT)
public final class AutoSprintHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post evt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LocalPlayer player = mc.player;
        if (!player.isSprinting() && canAutoSprint(player)) {
            player.setSprinting(true);
        }
    }

    private static boolean canAutoSprint(LocalPlayer player) {
        return wantsSprint(player)
                && !player.isShiftKeyDown()
                && hasEnoughFood(player)
                && !player.isUsingItem()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isFallFlying()
                && !player.onClimbable()
                && !player.isInLava()
                && !blockedByCollision(player)
                && !blockedBySurfaceWater(player)
                && (!player.isPassenger() || player.getVehicle().canSprint());
    }

    private static boolean wantsSprint(LocalPlayer player) {
        return player.isUnderWater()
                ? player.input.hasForwardImpulse()
                : player.input.forwardImpulse >= 0.8F;
    }

    private static boolean hasEnoughFood(LocalPlayer player) {
        return player.isPassenger()
                || player.getFoodData().getFoodLevel() > 6
                || player.mayFly();
    }

    private static boolean blockedByCollision(LocalPlayer player) {
        return player.horizontalCollision && !player.minorHorizontalCollision;
    }

    private static boolean blockedBySurfaceWater(LocalPlayer player) {
        return player.isInWater() && !player.isUnderWater();
    }

    private AutoSprintHandler() {
    }
}
