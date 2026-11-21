package com.custom.nodashbhop.wheel;

import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Isolated client-only bootstrapper for JCraft Ability Wheel v3.
 * Called strictly inside DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...).
 */
public final class WheelClientBootstrap {
    private WheelClientBootstrap() {}

    public static void init() {
        WheelController controller = new WheelController();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(WheelController.KEY_CAST);
            event.register(WheelController.KEY_WHEEL);
        });

        MinecraftForge.EVENT_BUS.register(controller);
        MinecraftForge.EVENT_BUS.register(controller.aboutParser);
        MinecraftForge.EVENT_BUS.register(new WheelHudOverlay(controller));
    }
}
