package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.client.compat.emi.EmiExportSupport;
import com.evandev.packdev_toolkit.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Copies a ready-to-paste {@code "translation.key": "Display Name"} lang entry to the
 * clipboard for whatever item is hovered in EMI, being looked at, or held.
 */
public class TranslationCopyManager {

    private TranslationCopyManager() {
    }

    public static void handleCopyKeyPress(Minecraft mc) {
        String descriptionId = resolveDescriptionId(mc);
        if (descriptionId == null) {
            return;
        }

        String clipboardData = "\"%s\": \"%s\"".formatted(descriptionId, I18n.get(descriptionId));
        mc.keyboardHandler.setClipboard(clipboardData);

        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("Copied to clipboard: " + clipboardData), true);
        }
    }

    private static String resolveDescriptionId(Minecraft mc) {
        if (Services.PLATFORM.isModLoaded("emi")) {
            ItemStack hovered = EmiExportSupport.getHoveredStack();
            if (!hovered.isEmpty()) {
                return hovered.getItem().getDescriptionId();
            }
        }

        if (mc.screen == null && mc.level != null) {
            HitResult hit = mc.hitResult;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                return mc.level.getBlockState(blockHit.getBlockPos()).getBlock().getDescriptionId();
            } else if (hit != null && hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
                return entityHit.getEntity().getType().getDescriptionId();
            }
        }

        if (mc.screen == null && mc.player != null) {
            ItemStack held = mc.player.getMainHandItem();
            if (!held.isEmpty()) {
                return held.getItem().getDescriptionId();
            }
        }

        return null;
    }
}
