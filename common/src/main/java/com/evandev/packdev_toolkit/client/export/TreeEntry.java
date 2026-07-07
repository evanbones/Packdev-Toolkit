package com.evandev.packdev_toolkit.client.export;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class TreeEntry extends ObjectSelectionList.Entry<TreeEntry> {
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

    @Override
    public @NotNull Component getNarration() {
        return Component.literal(node.name);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
        boolean isSelected = false;
        List<String> nestedFiles = new ArrayList<>();

        if (!node.isDirectory) {
            isSelected = screen.selectedPaths.contains(node.relativePath);
        } else {
            screen.gatherNestedFiles(node, nestedFiles);
            int selCount = 0;
            for (String path : nestedFiles) {
                if (screen.selectedPaths.contains(path)) {
                    selCount++;
                }
            }
            if (selCount == nestedFiles.size() && !nestedFiles.isEmpty()) {
                isSelected = true;
            }
        }

        int startX = left + 2;
        int indent = isSearching ? 0 : node.depth * 12;
        int cbX = startX + indent;
        int cbY = top + (height - 12) / 2;

        if (!isSearching) {
            screen.renderTreeGuides(guiGraphics, node, startX, top);
        }

        int borderCol = 0xFFA0A0A0;
        guiGraphics.fill(cbX, cbY, cbX + 10, cbY + 1, borderCol);
        guiGraphics.fill(cbX, cbY + 9, cbX + 10, cbY + 10, borderCol);
        guiGraphics.fill(cbX, cbY + 1, cbX + 1, cbY + 9, borderCol);
        guiGraphics.fill(cbX + 9, cbY + 1, cbX + 10, cbY + 9, borderCol);

        guiGraphics.fill(cbX + 1, cbY + 1, cbX + 9, cbY + 9, 0xFF000000);

        if (!node.isDirectory) {
            if (isSelected) {
                guiGraphics.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, 0xFF4CAF50);
            }
        } else {
            int selCount = 0;
            for (String path : nestedFiles) {
                if (screen.selectedPaths.contains(path)) {
                    selCount++;
                }
            }
            if (selCount == nestedFiles.size() && !nestedFiles.isEmpty()) {
                guiGraphics.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, 0xFF4CAF50);
            } else if (selCount > 0) {
                guiGraphics.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, 0xFF7F7F7F);
            }
        }

        ResourceLocation icon = getIcon();
        guiGraphics.blit(icon, cbX + 14, top + 1, 0, 0, 16, 16, 16, 16);

        String label;
        int labelColor;
        if (node.isDirectory) {
            label = node.name + "/";
            labelColor = 0xFFD4AF37;
        } else {
            label = isSearching ? node.relativePath : node.name;
            labelColor = 0xFFFFFF;
        }

        guiGraphics.drawString(screen.getFont(), label, cbX + 34, top + (height - 8) / 2, labelColor, false);
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
        int left = widget.getRowLeft();
        int startX = left + 2;
        int indent = isSearching ? 0 : node.depth * 12;
        int cbX = startX + indent;

        boolean isShift = Screen.hasShiftDown();
        boolean isCtrl = Screen.hasControlDown();

        if (isShift || isCtrl || (mouseX >= cbX && mouseX <= cbX + 10)) {
            boolean nowSelected;
            if (!node.isDirectory) {
                nowSelected = !screen.selectedPaths.contains(node.relativePath);
            } else {
                List<String> nestedFiles = new ArrayList<>();
                screen.gatherNestedFiles(node, nestedFiles);
                int selCount = 0;
                for (String path : nestedFiles) {
                    if (screen.selectedPaths.contains(path)) {
                        selCount++;
                    }
                }
                nowSelected = (selCount != nestedFiles.size());
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
