package com.xlpvp.main.speed;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;

public final class SpeedMod {

    public SpeedMod() {
        IEventBus modBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        assert modBus != null;
        modBus.register(CreativeBooks.class);
    }
}
