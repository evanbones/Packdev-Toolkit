package com.evandev.packdev_toolkit.client;

import com.evandev.packdev_toolkit.client.keybind.ModKeyBindings;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

public class ClientEvents {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientEvents::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.EXPORT);
        event.register(ModKeyBindings.COPY_TRANSLATION_KEY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientTickHandler.onClientTick(Minecraft.getInstance());
    }
}
