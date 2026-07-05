package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.config.ModConfig;

import java.nio.file.Path;

public class ExportPaths {
    public static Path assetsRoot(Path baseDir) {
        Path configured = Path.of(ModConfig.get().resourcePackExportDirectory);
        Path resolved = configured.isAbsolute() ? configured : baseDir.resolve(configured);
        return resolved.resolve("assets");
    }

    public static Path dataRoot(Path baseDir) {
        Path configured = Path.of(ModConfig.get().dataPackExportDirectory);
        Path resolved = configured.isAbsolute() ? configured : baseDir.resolve(configured);
        return resolved.resolve("data");
    }

    public static Path queriesRoot(Path baseDir) {
        Path configured = Path.of(ModConfig.get().queriesExportDirectory);
        Path resolved = configured.isAbsolute() ? configured : baseDir.resolve(configured);
        return resolved.resolve("queries");
    }
}
