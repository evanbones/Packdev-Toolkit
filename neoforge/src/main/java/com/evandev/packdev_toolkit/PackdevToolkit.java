package com.evandev.packdev_toolkit;

import com.evandev.packdev_toolkit.client.ClientConfigSetup;
import com.evandev.packdev_toolkit.client.ClientEvents;
import com.evandev.packdev_toolkit.command.ExportCommand;
import com.evandev.packdev_toolkit.command.QueryCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(Constants.MOD_ID)
public class PackdevToolkit {
    public PackdevToolkit(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);

        if (FMLEnvironment.dist.isClient()) {
            ClientConfigSetup.register(modContainer);
            ClientEvents.init(modEventBus);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        CommonClass.init();
    }

    private void registerCommands(final RegisterCommandsEvent event) {
        QueryCommand.register(event.getDispatcher(), event.getBuildContext());
        ExportCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}