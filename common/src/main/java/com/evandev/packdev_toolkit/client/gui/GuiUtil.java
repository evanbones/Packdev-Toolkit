package com.evandev.packdev_toolkit.client.gui;

import com.evandev.packdev_toolkit.Constants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class GuiUtil {
    public static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/background.png");

    public static final ResourceLocation SCROLLBAR_TRACK = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/scrollbar_track_vanilla");
    public static final ResourceLocation SCROLLBAR_THUMB = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/scrollbar_thumb_vanilla");

    public static final ResourceLocation TEXT_FIELD = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/text_field");
    public static final ResourceLocation TEXT_FIELD_HIGHLIGHTED = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/text_field_highlighted");

    public static final int PANEL_PADDING = 7;
    // Text drawn straight onto a panel sits this much further in, so it doesn't run into the light frame
    public static final int TEXT_INSET = 3;
    // The scrollbar sprites are 16px wide, but only columns 2-9 are opaque
    public static final int SCROLLBAR_WIDTH = 8;
    // Between a scrollbar and the well it scrolls
    public static final int SCROLLBAR_GAP = 2;
    private static final int SCROLLBAR_SPRITE_WIDTH = 16;
    private static final int SCROLLBAR_SPRITE_INSET = 2;
    public static final int TOP_BAR_HEIGHT = 28;
    public static final int TOP_BAR_COLOR = 0xDD181818;

    public static final int TEXT = 0xFFFFFF;
    public static final int SUBTEXT = 0xB5B5B5;
    public static final int FOLDER_TEXT = 0xE8C35A;
    public static final int ROW_HOVER = 0x22FFFFFF;
    public static final int ROW_SELECTED = 0x44FFFFFF;

    public static final int STATUS_OK = 0x55FF55;
    public static final int STATUS_ERROR = 0xFF5555;
    public static final int STATUS_PENDING = 0xFFCC00;
    public static final int STATUS_NEUTRAL = 0xA0A0A0;

    public static void drawNinePatch(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h, int u, int v, int cornerLength, int centerLength) {
        int corcen = cornerLength + centerLength;
        int innerWidth = w - cornerLength * 2;
        int innerHeight = h - cornerLength * 2;
        int coriw = cornerLength + innerWidth;
        int corih = cornerLength + innerHeight;

        blit(guiGraphics, texture, x, y, u, v, cornerLength, cornerLength, cornerLength, cornerLength);
        blit(guiGraphics, texture, x + cornerLength, y, u + cornerLength, v, innerWidth, cornerLength, centerLength, cornerLength);
        blit(guiGraphics, texture, x + coriw, y, u + corcen, v, cornerLength, cornerLength, cornerLength, cornerLength);
        blit(guiGraphics, texture, x, y + cornerLength, u, v + cornerLength, cornerLength, innerHeight, cornerLength, centerLength);
        blit(guiGraphics, texture, x + cornerLength, y + cornerLength, u + cornerLength, v + cornerLength, innerWidth, innerHeight, centerLength, centerLength);
        blit(guiGraphics, texture, x + coriw, y + cornerLength, u + corcen, v + cornerLength, cornerLength, innerHeight, cornerLength, centerLength);
        blit(guiGraphics, texture, x, y + corih, u, v + corcen, cornerLength, cornerLength, cornerLength, cornerLength);
        blit(guiGraphics, texture, x + cornerLength, y + corih, u + cornerLength, v + corcen, innerWidth, cornerLength, centerLength, cornerLength);
        blit(guiGraphics, texture, x + coriw, y + corih, u + corcen, v + corcen, cornerLength, cornerLength, cornerLength, cornerLength);
    }

    private static void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height, int srcWidth, int srcHeight) {
        guiGraphics.blit(texture, x, y, width, height, u, v, srcWidth, srcHeight, 256, 256);
    }

    public static void drawVanillaPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        drawNinePatch(guiGraphics, BACKGROUND, x, y, width, height, 0, 32, 8, 1);
    }

    public static void drawWell(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        blitSprite(guiGraphics, TEXT_FIELD, x, y, width, height);
    }

    public static void drawTopBar(GuiGraphics guiGraphics, int width) {
        guiGraphics.fill(0, 0, width, TOP_BAR_HEIGHT, TOP_BAR_COLOR);
    }

    public static void blitSprite(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.blitSprite(sprite, x, y, width, height);
        RenderSystem.disableBlend();
    }

    public static void drawScrollbar(GuiGraphics guiGraphics, int x, int y, int height, double scrollAmount, int maxScroll) {
        int spriteX = x - SCROLLBAR_SPRITE_INSET;
        blitSprite(guiGraphics, SCROLLBAR_TRACK, spriteX, y, SCROLLBAR_SPRITE_WIDTH, height);
        if (maxScroll <= 0) return;
        int thumbHeight = thumbHeight(height, maxScroll);
        int thumbY = y + (int) ((height - thumbHeight) * (scrollAmount / maxScroll));
        blitSprite(guiGraphics, SCROLLBAR_THUMB, spriteX, thumbY, SCROLLBAR_SPRITE_WIDTH, thumbHeight);
    }

    /**
     * The vertical scrollbar mirrored across the diagonal, so it uses the same sprites with the light still
     * coming from the top left.
     */
    public static void drawHorizontalScrollbar(GuiGraphics guiGraphics, int x, int y, int width, double scrollAmount, int maxScroll) {
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        // Swaps x and y; the matrix is its own transpose, so the column-major order doesn't matter
        pose.mulPose(new Matrix4f(0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1));
        // Mirroring flips the quads' winding, which would otherwise get them culled
        RenderSystem.disableCull();
        drawScrollbar(guiGraphics, 0, 0, width, scrollAmount, maxScroll);
        guiGraphics.flush();
        RenderSystem.enableCull();
        pose.popPose();
    }

    /**
     * Works along either axis: pass the mouse coordinate, start and length of the scrollbar in that direction.
     */
    public static double scrollAmountFromMouse(double mouseY, int y, int height, int maxScroll) {
        if (maxScroll <= 0) return 0;
        int thumbHeight = thumbHeight(height, maxScroll);
        int travel = height - thumbHeight;
        if (travel <= 0) return 0;
        double fraction = (mouseY - y - thumbHeight / 2.0) / travel;
        return Math.max(0, Math.min(1, fraction)) * maxScroll;
    }

    private static int thumbHeight(int height, int maxScroll) {
        if (maxScroll <= 0) return height;
        float visibleFraction = (float) height / (height + maxScroll);
        return Math.max(16, (int) (height * visibleFraction));
    }

    public static void drawCheckbox(GuiGraphics guiGraphics, int x, int y, int size, boolean checked, boolean partial) {
        guiGraphics.fill(x, y, x + size, y + size, 0xFFA0A0A0);
        guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF1E1E1E);
        if (checked) {
            guiGraphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFF55C155);
        } else if (partial) {
            guiGraphics.fill(x + 2, y + size / 2 - 1, x + size - 2, y + size / 2 + 1, 0xFFA0A0A0);
        }
    }

    public static void drawCheckerboard(GuiGraphics guiGraphics, int x, int y, int width, int height, int cell) {
        guiGraphics.enableScissor(x, y, x + width, y + height);
        for (int cy = 0; cy < height; cy += cell) {
            for (int cx = 0; cx < width; cx += cell) {
                boolean dark = ((cx / cell) + (cy / cell)) % 2 == 0;
                guiGraphics.fill(x + cx, y + cy, x + cx + cell, y + cy + cell, dark ? 0xFF2B2B2B : 0xFF3A3A3A);
            }
        }
        guiGraphics.disableScissor();
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    /**
     * Cuts text that doesn't fit off at the start, keeping the end visible, e.g. {@code ".../textures/item"}.
     */
    public static String ellipsizeStart(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return "..." + font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("...")), true);
    }
}
