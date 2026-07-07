package com.evandev.packdev_toolkit.client;

import com.evandev.packdev_toolkit.client.export.ModSelectionScreen;
import com.evandev.packdev_toolkit.client.keybind.ModKeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

public class ClientEvents {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientEvents::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::registerClientCommands);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.EXPORT);
        event.register(ModKeyBindings.COPY_TRANSLATION_KEY);
        event.register(ModKeyBindings.EXPORT_DESCRIPTION);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientTickHandler.onClientTick(Minecraft.getInstance());
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("packdev")
                .then(Commands.literal("browse")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.tell(() -> mc.setScreen(new ModSelectionScreen()));
                            return 1;
                        })
                )
        );
    }
}
