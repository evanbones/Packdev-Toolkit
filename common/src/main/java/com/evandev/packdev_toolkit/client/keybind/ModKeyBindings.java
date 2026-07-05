package com.evandev.packdev_toolkit.client.keybind;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static final String CATEGORY = "key.categories.packdev_toolkit";

    public static final KeyMapping EXPORT = new KeyMapping(
            "key.packdev_toolkit.export",
            GLFW.GLFW_KEY_O,
            CATEGORY
    );

    public static final KeyMapping COPY_TRANSLATION_KEY = new KeyMapping(
            "key.packdev_toolkit.copy_translation_key",
            GLFW.GLFW_KEY_J,
            CATEGORY
    );

    public static final KeyMapping EXPORT_DESCRIPTION = new KeyMapping(
            "key.packdev_toolkit.export_description",
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );
}
