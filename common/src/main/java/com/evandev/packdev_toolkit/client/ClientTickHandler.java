package com.evandev.packdev_toolkit.client;

import com.evandev.packdev_toolkit.client.export.ExportManager;
import com.evandev.packdev_toolkit.client.export.TranslationCopyManager;
import com.evandev.packdev_toolkit.client.keybind.ModKeyBindings;
import com.evandev.packdev_toolkit.mixin.client.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

public class ClientTickHandler {
    private static boolean exportWasDown = false;
    private static boolean copyWasDown = false;

    private ClientTickHandler() {
    }

    public static void onClientTick(Minecraft mc) {
        boolean exportDown = isPhysicallyDown(mc, ModKeyBindings.EXPORT);
        if (exportDown && !exportWasDown && !isTypingInField(mc)) {
            ExportManager.handleExportKeyPress(mc, Screen.hasShiftDown());
        }
        exportWasDown = exportDown;

        boolean copyDown = isPhysicallyDown(mc, ModKeyBindings.COPY_TRANSLATION_KEY);
        if (copyDown && !copyWasDown && !isTypingInField(mc)) {
            TranslationCopyManager.handleCopyKeyPress(mc);
        }
        copyWasDown = copyDown;
    }

    private static boolean isPhysicallyDown(Minecraft mc, KeyMapping mapping) {
        InputConstants.Key key = ((KeyMappingAccessor) mapping).packdev_toolkit$getKey();
        return key.getType() == InputConstants.Type.KEYSYM
                && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    private static boolean isTypingInField(Minecraft mc) {
        return mc.screen != null && mc.screen.getFocused() instanceof EditBox editBox && editBox.canConsumeInput();
    }
}
