package com.xlpvp.main.speed;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public final class CreativeBooks {

    @SubscribeEvent
    public static void fillCombatTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.COMBAT) {
            return;
        }

        Holder<Enchantment> speed = event.getParameters()
                .holders()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.SPEED);

        for (int level = 1; level <= 3; level++) {
            ItemStack book = EnchantedBookItem.createForEnchantment(
                    new EnchantmentInstance(speed, level));
            event.accept(book);
        }
    }

    private CreativeBooks() {
    }
}
