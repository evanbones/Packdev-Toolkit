package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.client.gui.ScrollListWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ResourceListWidget extends ScrollListWidget<TreeEntry> {
    private final PackBrowserScreen screen;
    private int contentWidth = 0;

    public ResourceListWidget(PackBrowserScreen screen, int x, int y, int width, int height, int itemHeight) {
        super(x, y, width, height, itemHeight);
        this.screen = screen;
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
        // Measured once here rather than every frame, since search results can run into the thousands
        this.contentWidth = 0;
        for (TreeEntry entry : this.children()) {
            this.contentWidth = Math.max(this.contentWidth, entry.getContentWidth());
        }
        this.clampScroll();
    }

    @Override
    protected int getContentWidth() {
        return this.contentWidth;
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
