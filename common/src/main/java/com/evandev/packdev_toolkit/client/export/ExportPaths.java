package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.config.ModConfig;

import java.nio.file.Path;

/**
 * Resolves the configured export root (see {@link ModConfig#exportDirectory}) against a
 * given base directory. Callers pass the client game directory or the running server's
 * director.
 */
public class ExportPaths {
    public static Path resolveRoot(Path baseDir) {
        Path configured = Path.of(ModConfig.get().exportDirectory);
        return configured.isAbsolute() ? configured : baseDir.resolve(configured);
    }

    public static Path assetsRoot(Path baseDir) {
        return resolveRoot(baseDir).resolve("assets");
    }

    public static Path dataRoot(Path baseDir) {
        return resolveRoot(baseDir).resolve("data");
    }

    public static Path queriesRoot(Path baseDir) {
        return resolveRoot(baseDir).resolve("queries");
    }
}
