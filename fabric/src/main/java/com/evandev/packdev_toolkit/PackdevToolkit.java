package com.evandev.packdev_toolkit;

import com.evandev.packdev_toolkit.command.ExportCommand;
import com.evandev.packdev_toolkit.command.QueryCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class PackdevToolkit implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            QueryCommand.register(dispatcher, registryAccess);
            ExportCommand.register(dispatcher);
        });
    }
}