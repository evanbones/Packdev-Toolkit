package com.evandev.packdev_toolkit.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * An {@link EditBox} drawn with the toolkit's text field sprite. Right-clicking clears it.
 */
public class ModEditBox extends EditBox {
    private static final int TEXT_INSET_X = 4;

    public ModEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.setBordered(false);
    }

    @Override
    public int getInnerWidth() {
        return this.width - TEXT_INSET_X * 2;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX - TEXT_INSET_X, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.isVisible() && this.isMouseOver(mouseX, mouseY)) {
            this.setValue("");
            this.setFocused(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.isVisible()) return;
        GuiUtil.blitSprite(guiGraphics, this.isFocused() ? GuiUtil.TEXT_FIELD_HIGHLIGHTED : GuiUtil.TEXT_FIELD,
                this.getX(), this.getY(), this.getWidth(), this.getHeight());

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(TEXT_INSET_X, (this.height - 8) / 2f, 0);
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.pose().popPose();
    }
}
