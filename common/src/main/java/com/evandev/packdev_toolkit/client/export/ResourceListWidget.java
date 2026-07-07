package com.evandev.packdev_toolkit.client.export;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ResourceListWidget extends ObjectSelectionList<TreeEntry> {
    private final PackBrowserScreen screen;

    public ResourceListWidget(PackBrowserScreen screen, Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.screen = screen;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width - 6;
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
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
