package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.client.gui.GuiUtil;
import com.evandev.packdev_toolkit.client.gui.ScrollListWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class TreeEntry implements ScrollListWidget.Entry {
    static final int INDENT = 10;
    private static final int CHECKBOX_SIZE = 10;
    private static final int LEFT_PAD = 3;
    private static final int RIGHT_PAD = 4;

    public final TreeNode node;
    private final PackBrowserScreen screen;
    private final ResourceListWidget widget;
    private final boolean isSearching;

    public TreeEntry(PackBrowserScreen screen, ResourceListWidget widget, TreeNode node, boolean isSearching) {
        this.screen = screen;
        this.widget = widget;
        this.node = node;
        this.isSearching = isSearching;
    }

    private int checkboxX(int left) {
        return left + LEFT_PAD + (isSearching ? 0 : node.depth * INDENT);
    }

    private int labelX(int left) {
        return checkboxX(left) + CHECKBOX_SIZE + 22;
    }

    private String getLabel() {
        return node.isDirectory ? node.name + "/" : node.name;
    }

    /**
     * Search results leave out the folder, which is shortened to fit instead, so one deep path doesn't make the
     * whole list scroll.
     */
    int getContentWidth() {
        return labelX(0) + screen.getFont().width(getLabel()) + RIGHT_PAD;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered) {
        if (node == screen.getSelectedNode()) {
            guiGraphics.fill(left, top, left + width, top + height, GuiUtil.ROW_SELECTED);
        } else if (isHovered) {
            guiGraphics.fill(left, top, left + width, top + height, GuiUtil.ROW_HOVER);
        }

        if (!isSearching) {
            screen.renderTreeGuides(guiGraphics, node, left + LEFT_PAD, top, height);
        }

        int cbX = checkboxX(left);
        int cbY = top + (height - CHECKBOX_SIZE) / 2;
        renderCheckbox(guiGraphics, cbX, cbY);

        RenderSystem.enableBlend();
        guiGraphics.blit(getIcon(), cbX + CHECKBOX_SIZE + 3, top + (height - 16) / 2, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();

        Font font = screen.getFont();
        int labelX = labelX(left);
        int labelY = top + (height - 8) / 2;
        // Labels are drawn in full and the list scrolls sideways, so only what's inside the well is visible
        int visibleLeft = widget.getRowLeft();
        int visibleRight = visibleLeft + widget.getRowWidth();
        String label = getLabel();
        boolean truncated = labelX < visibleLeft || labelX + font.width(label) > visibleRight - RIGHT_PAD;
        guiGraphics.drawString(font, label, labelX, labelY, node.isDirectory ? GuiUtil.FOLDER_TEXT : GuiUtil.TEXT, true);

        if (isSearching) {
            // The folder fills whatever space is left in view, cut from the start so the nearest folders stay visible
            int folderX = labelX + font.width(label) + 6;
            int folderWidth = visibleRight - folderX - RIGHT_PAD;
            String folder = node.parent != null ? node.parent.relativePath : "";
            // A sliver like "...s" doesn't help, so only show the folder when a useful amount of it fits
            String shownFolder = folderWidth >= 40 ? GuiUtil.ellipsizeStart(font, folder, folderWidth) : "";
            truncated |= !shownFolder.equals(folder);
            guiGraphics.drawString(font, shownFolder, folderX, labelY, GuiUtil.SUBTEXT, false);
        }

        if (isHovered && truncated) {
            screen.setTooltipForNextRenderPass(Component.literal(node.relativePath));
        }
    }

    private void renderCheckbox(GuiGraphics guiGraphics, int x, int y) {
        int selected;
        int total;
        if (!node.isDirectory) {
            selected = screen.selectedPaths.contains(node.relativePath) ? 1 : 0;
            total = 1;
        } else {
            List<String> nestedFiles = new ArrayList<>();
            screen.gatherNestedFiles(node, nestedFiles);
            selected = (int) nestedFiles.stream().filter(screen.selectedPaths::contains).count();
            total = nestedFiles.size();
        }

        GuiUtil.drawCheckbox(guiGraphics, x, y, CHECKBOX_SIZE, total > 0 && selected == total, selected > 0);
    }

    private ResourceLocation getIcon() {
        if (node.isDirectory) {
            return PackBrowserScreen.FOLDER_ICON;
        }
        String lower = node.name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) {
            return PackBrowserScreen.JSON_ICON;
        }
        if (lower.endsWith(".png")) {
            return PackBrowserScreen.IMAGE_ICON;
        }
        if (lower.endsWith(".zip") || lower.endsWith(".jar")) {
            return PackBrowserScreen.ZIP_ICON;
        }
        if (lower.endsWith(".mcmeta")) {
            return PackBrowserScreen.MCMETA_ICON;
        }
        return PackBrowserScreen.FILE_ICON;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cbX = checkboxX(widget.getScrolledRowLeft());

        boolean isShift = Screen.hasShiftDown();
        boolean isCtrl = Screen.hasControlDown();

        if (isShift || isCtrl || (mouseX >= cbX && mouseX <= cbX + CHECKBOX_SIZE)) {
            boolean nowSelected;
            if (!node.isDirectory) {
                nowSelected = !screen.selectedPaths.contains(node.relativePath);
            } else {
                List<String> nestedFiles = new ArrayList<>();
                screen.gatherNestedFiles(node, nestedFiles);
                nowSelected = !screen.selectedPaths.containsAll(nestedFiles);
            }

            if (isShift && screen.lastClickedCheckboxPath != null) {
                screen.selectRange(screen.lastClickedCheckboxPath, node.relativePath, nowSelected);
            } else {
                screen.toggleSelection(node, nowSelected);
            }
            screen.lastClickedCheckboxPath = node.relativePath;
        } else {
            if (node.isDirectory) {
                if (screen.expandedPaths.contains(node.relativePath)) {
                    screen.expandedPaths.remove(node.relativePath);
                } else {
                    screen.expandedPaths.add(node.relativePath);
                }
                widget.updateFilter(screen.searchBox != null ? screen.searchBox.getValue() : "");
            } else {
                screen.selectFile(node);
            }
        }
        return true;
    }
}
