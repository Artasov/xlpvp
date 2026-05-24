package com.xlpvp;

import com.xlpvp.main.speed.SpeedMod;
import net.neoforged.fml.common.Mod;

/**
 * Главный класс мода. Регистрирует обработчики на общую шину NeoForge.
 */
@Mod(Core.MODID)
public final class Core {
    public static final String MODID = "xlpvp";

    public Core() {
        new SpeedMod();
    }
}
