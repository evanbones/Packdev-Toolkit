package com.evandev.packdev_toolkit.client.export;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Captures every non-atlas texture bound via {@code RenderSystem.setShaderTexture} while
 * {@link #isCapturing()} is true. Fed by {@code RenderSystemMixin}; used to discover the
 * concrete texture(s) a block entity or entity renderer draws, since those aren't reachable
 * from a baked model's quads the way block/item textures are.
 */
public class TextureCatcher {
    private static final Set<ResourceLocation> capturedTextures = new HashSet<>();
    private static boolean capturing = false;

    private TextureCatcher() {
    }

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
}
