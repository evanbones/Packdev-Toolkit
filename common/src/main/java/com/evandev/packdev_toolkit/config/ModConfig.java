package com.evandev.packdev_toolkit.config;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = Services.PLATFORM.getConfigDirectory().resolve(Constants.MOD_ID + ".json").toFile();
    private static ModConfig INSTANCE;

    public String resourcePackExportDirectory = "packdev_toolkit_resource_pack";
    public String dataPackExportDirectory = "packdev_toolkit_data_pack";
    public String queriesExportDirectory = "packdev_toolkit_queries";
    public boolean openFolderOnExport = true;

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                Constants.LOG.error("Failed to load " + Constants.MOD_ID + ".json", e);
            }
        }
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
            save();
        } else {
            INSTANCE.validate();
        }
    }

    private void validate() {
        if (resourcePackExportDirectory == null || resourcePackExportDirectory.isBlank()) {
            resourcePackExportDirectory = "packdev_toolkit_resource_pack";
        }
        if (dataPackExportDirectory == null || dataPackExportDirectory.isBlank()) {
            dataPackExportDirectory = "packdev_toolkit_data_pack";
        }
        if (queriesExportDirectory == null || queriesExportDirectory.isBlank()) {
            queriesExportDirectory = "packdev_toolkit_queries";
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            Constants.LOG.error("Failed to save " + Constants.MOD_ID + ".json", e);
        }
    }

    public static Screen createScreen(Screen parent) {
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("config.packdev_toolkit.title"))
                .save(ModConfig::save);

        ConfigCategory.Builder general = ConfigCategory.createBuilder()
                .name(Component.translatable("config.packdev_toolkit.category.general"))
                .option(createStringOption("resource_pack_export_directory", "packdev_toolkit_resource_pack", () -> get().resourcePackExportDirectory, val -> get().resourcePackExportDirectory = val))
                .option(createStringOption("data_pack_export_directory", "packdev_toolkit_data_pack", () -> get().dataPackExportDirectory, val -> get().dataPackExportDirectory = val))
                .option(createStringOption("queries_export_directory", "packdev_toolkit_queries", () -> get().queriesExportDirectory, val -> get().queriesExportDirectory = val))
                .option(createBoolOption("open_folder_on_export", true, () -> get().openFolderOnExport, val -> get().openFolderOnExport = val));

        return builder.category(general.build()).build().generateScreen(parent);
    }

    private static Option<Boolean> createBoolOption(String name, boolean defaultValue, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("config.packdev_toolkit.option." + name))
                .description(OptionDescription.of(Component.translatable("config.packdev_toolkit.option." + name + ".desc")))
                .binding(defaultValue, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<String> createStringOption(String name, String defaultValue, Supplier<String> getter, Consumer<String> setter) {
        return Option.<String>createBuilder()
                .name(Component.translatable("config.packdev_toolkit.option." + name))
                .description(OptionDescription.of(Component.translatable("config.packdev_toolkit.option." + name + ".desc")))
                .binding(defaultValue, getter, setter)
                .controller(StringControllerBuilder::create)
                .build();
    }
}