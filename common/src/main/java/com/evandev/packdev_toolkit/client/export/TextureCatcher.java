package com.evandev.packdev_toolkit.client.export;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

public class TextureCatcher {
    private static final Set<ResourceLocation> capturedTextures = new HashSet<>();
    private static boolean capturing = false;

    public static void start() {
        capturing = true;
        capturedTextures.clear();
    }

    public static Set<ResourceLocation> stop() {
        capturing = false;
        return new HashSet<>(capturedTextures);
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static void addTexture(ResourceLocation texture) {
        if (!capturing || texture == null) {
            return;
        }

        String path = texture.getPath();
        if (path.contains("light_map")
                || path.startsWith("atlas/")
                || path.startsWith("textures/atlas/")
                || path.contains("colormap/")) {
            return;
        }

        capturedTextures.add(texture);
    }

    /**
     * Records an atlas sprite by its sprite id, e.g. {@code minecraft:entity/chest/normal}.
     */
    public static void addSprite(ResourceLocation sprite) {
        if (capturing && sprite != null) {
            capturedTextures.add(sprite);
        }
    }
}
