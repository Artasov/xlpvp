package com.xlpvp.main.speed;

import com.xlpvp.Core;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public final class ModEnchantments {

    public static final ResourceLocation SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Core.MODID, "speed");

    public static final ResourceKey<Enchantment> SPEED =
            ResourceKey.create(Registries.ENCHANTMENT, SPEED_ID);

    private ModEnchantments() {
    }
}
