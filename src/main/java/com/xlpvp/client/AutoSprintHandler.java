package com.xlpvp.client;

import com.xlpvp.Core;
import com.xlpvp.mixin.LocalPlayerSprintInvoker;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Core.MODID, value = Dist.CLIENT)
public final class AutoSprintHandler {

    private static final int RETRY_DELAY_TICKS = 1;

    private static boolean wasTryingToSprint;
    private static int retryDelayTicks;

    @SubscribeEvent
    public static void onMovementKey(InputEvent.Key event) {
        onMovementButton(InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction());
    }

    @SubscribeEvent
    public static void onMovementMouseButton(InputEvent.MouseButton.Post event) {
        onMovementButton(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction());
    }

    private static void onMovementButton(InputConstants.Key key, int action) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.screen != null || player == null || !player.isSprinting() || player.isPassenger()) return;
        if (player.isSwimming() || player.isFallFlying() || player.getAbilities().flying) return;
        boolean releasedForward = action == InputConstants.RELEASE && mc.options.keyUp.getKey().equals(key);
        boolean pressedBack = action == InputConstants.PRESS && mc.options.keyDown.getKey().equals(key) && mc.options.keyDown.isDown();
        if (releasedForward || pressedBack) {
            // A complete W/S tap between movement ticks would otherwise never reach the server.
            player.setSprinting(false);
            ((LocalPlayerSprintInvoker) player).xlpvp$sendSprintChange();
        }
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre evt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        LocalPlayer player = mc.player;
        if ((retryDelayTicks <= 0 || !wasTryingToSprint) && wantsSprintFromKeys(mc) && canAutoSprintFromKeys(mc, player)) {
            startSprinting(player, true);
        }
    }

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent evt) {
        if (!(evt.getEntity() instanceof LocalPlayer player)) return;
        if (Minecraft.getInstance().player != player) return;

        boolean tryingToSprint = wantsSprint(player);
        boolean justStartedTrying = tryingToSprint && !wasTryingToSprint;
        wasTryingToSprint = tryingToSprint;

        if (!tryingToSprint) {
            retryDelayTicks = 0;
            return;
        }

        if (!canAutoSprint(player)) {
            if (blockedByCollision(player) || blockedBySurfaceWater(player)) {
                retryDelayTicks = RETRY_DELAY_TICKS;
            }
            return;
        }

        if (retryDelayTicks > 0 && !justStartedTrying) {
            retryDelayTicks--;
            return;
        }

        startSprinting(player, false);
    }

    private static void startSprinting(LocalPlayer player, boolean sendPacketNow) {
        if (player.isSprinting()) {
            return;
        }

        player.setSprinting(true);

        if (sendPacketNow) {
            ((LocalPlayerSprintInvoker) player).xlpvp$sendSprintChange();
        }
    }

    private static boolean wantsSprintFromKeys(Minecraft mc) {
        return mc.options.keyUp.isDown() && !mc.options.keyDown.isDown();
    }

    private static boolean canAutoSprintFromKeys(Minecraft mc, LocalPlayer player) {
        return !mc.options.keyShift.isDown()
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
