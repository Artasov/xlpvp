package com.xlpvp.main.speed;

import com.xlpvp.Core;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

@EventBusSubscriber(modid = Core.MODID)
public final class SpeedAnvilHandler {

    private static final int SPEED_ONE_COST = 50;
    private static final int SPEED_TWO_COST = 70;
    private static final int SPEED_THREE_COST = 100;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty() || !right.is(Items.ENCHANTED_BOOK)) {
            return;
        }

        Holder<Enchantment> speed = event.getPlayer()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.SPEED);

        int bookLevel = EnchantmentHelper.getEnchantmentsForCrafting(right).getLevel(speed);
        if (bookLevel <= 0 || !left.supportsEnchantment(speed)) {
            return;
        }

        ItemEnchantments leftEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(left);
        int currentLevel = leftEnchantments.getLevel(speed);
        int resultLevel = currentLevel == bookLevel ? currentLevel + 1 : Math.max(currentLevel, bookLevel);
        resultLevel = Math.min(resultLevel, speed.value().getMaxLevel());
        if (resultLevel <= currentLevel) {
            return;
        }

        ItemStack output = left.copy();
        output.setCount(1);
        applyName(event, left, output);
        applyRepairCost(left, right, output);
        int finalResultLevel = resultLevel;
        EnchantmentHelper.updateEnchantments(output, enchantments -> enchantments.set(speed, finalResultLevel));

        event.setOutput(output);
        event.setCost(costFor(resultLevel));
        event.setMaterialCost(1);
    }

    private static int costFor(int level) {
        return switch (level) {
            case 1 -> SPEED_ONE_COST;
            case 2 -> SPEED_TWO_COST;
            default -> SPEED_THREE_COST;
        };
    }

    private static void applyName(AnvilUpdateEvent event, ItemStack input, ItemStack output) {
        String name = event.getName();
        if (name == null) {
            return;
        }
        if (StringUtil.isBlank(name)) {
            if (input.has(DataComponents.CUSTOM_NAME)) {
                output.remove(DataComponents.CUSTOM_NAME);
            }
            return;
        }
        if (!name.equals(input.getHoverName().getString())) {
            output.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
    }

    private static void applyRepairCost(ItemStack left, ItemStack right, ItemStack output) {
        int repairCost = Math.max(
                left.getOrDefault(DataComponents.REPAIR_COST, 0),
                right.getOrDefault(DataComponents.REPAIR_COST, 0));
        output.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(repairCost));
    }

    private SpeedAnvilHandler() {
    }
}
