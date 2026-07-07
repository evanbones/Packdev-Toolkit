package com.evandev.packdev_toolkit.client.export;

import java.util.ArrayList;
import java.util.List;

class TreeNode {
    public final String name;
    public final String relativePath;
    public final boolean isDirectory;
    public final int depth;
    public final TreeNode parent;
    public final List<TreeNode> children = new ArrayList<>();

    public TreeNode(String name, String relativePath, boolean isDirectory, int depth, TreeNode parent) {
        this.name = name;
        this.relativePath = relativePath;
        this.isDirectory = isDirectory;
        this.depth = depth;
        this.parent = parent;
    }
}
