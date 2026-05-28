package com.xlpvp.mixin;

import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {

    @ModifyConstant(method = "renderLabels", constant = @Constant(intValue = 40))
    private int xlpvp$showHighAnvilCost(int original) {
        return Integer.MAX_VALUE;
    }
}
