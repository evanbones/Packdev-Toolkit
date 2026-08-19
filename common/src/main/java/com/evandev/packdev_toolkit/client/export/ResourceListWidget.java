package com.evandev.packdev_toolkit.client.export;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ResourceListWidget extends ObjectSelectionList<TreeEntry> {
    private final PackBrowserScreen screen;
    private int scrollX = 0;

    public ResourceListWidget(PackBrowserScreen screen, Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.screen = screen;
    }

    int getScrollX() {
        return Math.max(0, Math.min(scrollX, getMaxScrollX()));
    }

    void setScrollX(int scrollX) {
        this.scrollX = Math.max(0, Math.min(scrollX, getMaxScrollX()));
    }

    public int getMaxScrollX() {
        int maxWidth = 0;
        for (TreeEntry entry : this.children()) {
            maxWidth = Math.max(maxWidth, entry.getContentWidth());
        }
        int extraPadding = 20;
        return Math.max(0, maxWidth - this.getRowWidth() + extraPadding);
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width - 6;
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Screen.hasShiftDown() && scrollY != 0) {
            this.setScrollX(this.getScrollX() - (int) (scrollY * 10));
            return true;
        }
        if (scrollX != 0) {
            this.setScrollX(this.getScrollX() - (int) (scrollX * 10));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void updateFilter(String query) {
        this.clearEntries();
        if (!query.isEmpty()) {
            String lower = query.toLowerCase(Locale.ROOT);
            addMatchingFiles(screen.getTreeRoot(), lower);
        } else {
            List<TreeNode> visibleNodes = new ArrayList<>();
            screen.addVisibleNodes(screen.getTreeRoot(), visibleNodes);
            for (TreeNode node : visibleNodes) {
                this.addEntry(new TreeEntry(screen, this, node, false));
            }
        }
        this.scrollX = Math.max(0, Math.min(this.scrollX, this.getMaxScrollX()));
    }

    private void addMatchingFiles(TreeNode node, String lower) {
        for (TreeNode child : node.children) {
            if (!child.isDirectory && child.relativePath.toLowerCase(Locale.ROOT).contains(lower)) {
                this.addEntry(new TreeEntry(screen, this, child, true));
            } else if (child.isDirectory) {
                addMatchingFiles(child, lower);
            }
        }
    }
}
