package com.evandev.packdev_toolkit.client;

import com.evandev.packdev_toolkit.client.export.ModSelectionScreen;
import com.evandev.packdev_toolkit.client.keybind.ModKeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;

public class PackdevToolkitClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.EXPORT);
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.COPY_TRANSLATION_KEY);
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.EXPORT_DESCRIPTION);

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickHandler::onClientTick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("packdev")
                    .then(ClientCommandManager.literal("browse")
                            .executes(context -> {
                                Minecraft mc = Minecraft.getInstance();
                                mc.tell(() -> mc.setScreen(new ModSelectionScreen()));
                                return 1;
                            })
                    )
            );
        });
    }
}