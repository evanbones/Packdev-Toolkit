package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.platform.ModJarInfo;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PackBrowserScreen extends Screen {
    static final ResourceLocation FOLDER_ICON = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/icons/folder.png");
    static final ResourceLocation FILE_ICON = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/icons/file.png");
    static final ResourceLocation JSON_ICON = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/icons/json.png");
    static final ResourceLocation IMAGE_ICON = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/icons/image.png");
    static final ResourceLocation ZIP_ICON = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/icons/zip.png");
    static final ResourceLocation MCMETA_ICON = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/icons/mcmeta.png");
    final Set<String> selectedPaths = new HashSet<>();
    final TreeNode treeRoot = new TreeNode("", "", true, -1, null);
    final Set<String> expandedPaths = new HashSet<>();
    private final Screen parent;
    private final ModJarInfo mod;
    private final List<String> allFilePaths = new ArrayList<>();
    EditBox searchBox;
    String lastClickedCheckboxPath = null;
    private ResourceListWidget resourceListWidget;
    private TreeNode selectedNode = null;
    private String selectedFileText = null;
    private String selectedFileName = "";
    private String saveStatusMessage = "No file selected";
    private int saveStatusColor = 0x888888;
    private String lastValidatedText = "";
    private ResourceLocation previewTextureLocation = null;
    private DynamicTexture previewTexture = null;
    private NativeImage previewImage = null;

    private MultiLineEditBox jsonEditorBox;
    private EditBox renameBox;
    private Button saveRenameBtn;
    private Button saveEditBtn;
    private Button openOnHostBtn;

    public PackBrowserScreen(Screen parent, ModJarInfo mod) {
        super(Component.literal("Packdev Toolkit Browser"));
        this.parent = parent;
        this.mod = mod;
        scanModResources();
        buildTree();
    }

    Font getFont() {
        return this.font;
    }

    TreeNode getTreeRoot() {
        return this.treeRoot;
    }

    private void scanModResources() {
        allFilePaths.clear();
        for (Path root : mod.rootPaths()) {
            try {
                if (Files.exists(root)) {
                    try (var stream = Files.walk(root)) {
                        stream.filter(Files::isRegularFile)
                                .forEach(p -> {
                                    try {
                                        String rel = root.relativize(p).toString().replace('\\', '/');
                                        while (rel.startsWith("/")) {
                                            rel = rel.substring(1);
                                        }
                                        if (rel.startsWith("assets/") || rel.startsWith("data/")) {
                                            allFilePaths.add(rel);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                });
                    }
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to scan mod resources for {}", mod.id(), e);
            }
        }
        allFilePaths.sort(String::compareTo);
    }

    private void buildTree() {
        treeRoot.children.clear();
        for (String path : allFilePaths) {
            String[] parts = path.split("/");
            TreeNode current = treeRoot;
            StringBuilder currentPath = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) continue;
                if (!currentPath.isEmpty()) {
                    currentPath.append("/");
                }
                currentPath.append(part);
                boolean isLast = (i == parts.length - 1);
                boolean isDir = !isLast;

                TreeNode child = null;
                for (TreeNode c : current.children) {
                    if (c.name.equals(part) && c.isDirectory == isDir) {
                        child = c;
                        break;
                    }
                }
                if (child == null) {
                    child = new TreeNode(part, currentPath.toString(), isDir, current.depth + 1, current);
                    current.children.add(child);
                }
                current = child;
            }
        }
        sortTreeNodes(treeRoot);
    }

    private void sortTreeNodes(TreeNode node) {
        node.children.sort((a, b) -> {
            if (a.isDirectory != b.isDirectory) {
                return a.isDirectory ? -1 : 1;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        for (TreeNode child : node.children) {
            if (child.isDirectory) {
                sortTreeNodes(child);
            }
        }
    }

    void addVisibleNodes(TreeNode node, List<TreeNode> list) {
        for (TreeNode child : node.children) {
            list.add(child);
            if (child.isDirectory && expandedPaths.contains(child.relativePath)) {
                addVisibleNodes(child, list);
            }
        }
    }

    @Override
    protected void init() {
        this.searchBox = new EditBox(this.font, 10, 55, 170, 18, Component.literal("Search files"));
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        this.resourceListWidget = new ResourceListWidget(this, this.minecraft, 174, this.height - 138, 78, 18);
        this.resourceListWidget.setX(8);
        this.addRenderableWidget(this.resourceListWidget);

        this.jsonEditorBox = new MultiLineEditBox(this.font, 190, 102, this.width - 200, this.height - 152, Component.literal("Select a file to edit"), Component.literal("JSON Editor"));
        this.addRenderableWidget(this.jsonEditorBox);

        this.renameBox = new EditBox(this.font, 250, 82, 110, 16, Component.literal("New Name"));
        this.addRenderableWidget(this.renameBox);

        this.saveRenameBtn = Button.builder(Component.literal("Rename"), btn -> saveRename())
                .bounds(365, 82, 50, 16)
                .build();
        this.addRenderableWidget(this.saveRenameBtn);

        this.saveEditBtn = Button.builder(Component.literal("Save"), btn -> saveEdit())
                .bounds(420, 82, 40, 16)
                .build();
        this.addRenderableWidget(this.saveEditBtn);

        this.openOnHostBtn = Button.builder(Component.literal("Open"), btn -> openOnHost())
                .bounds(465, 82, 40, 16)
                .build();
        this.addRenderableWidget(this.openOnHostBtn);

        updateRightPanelWidgets();
        onSearchChanged(this.searchBox.getValue());

        int buttonsY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Export"), btn -> exportSelected())
                .bounds(10, buttonsY, 50, 18)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Select All"), btn -> selectAllFiltered())
                .bounds(65, buttonsY, 65, 18)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(135, buttonsY, 40, 18).build());
    }

    private void onSearchChanged(String text) {
        if (this.resourceListWidget != null) {
            this.resourceListWidget.updateFilter(text);
        }
    }

    private void updateRightPanelWidgets() {
        boolean isJson = selectedNode != null && !selectedNode.isDirectory && selectedNode.relativePath.endsWith(".json");
        if (this.jsonEditorBox != null) {
            this.jsonEditorBox.setValue(selectedFileText == null ? "" : selectedFileText);
            this.jsonEditorBox.active = isJson;
            this.jsonEditorBox.visible = isJson;
        }
        if (this.renameBox != null) {
            this.renameBox.setValue(selectedFileName);
            this.renameBox.setVisible(isJson);
        }
        if (this.saveRenameBtn != null) {
            this.saveRenameBtn.visible = isJson;
        }
        if (this.saveEditBtn != null) {
            this.saveEditBtn.visible = isJson;
        }
        if (this.openOnHostBtn != null) {
            this.openOnHostBtn.visible = isJson;
        }
    }

    void selectFile(TreeNode node) {
        this.selectedNode = node;
        this.selectedFileName = node.name;
        this.selectedFileText = "";
        this.saveStatusMessage = "Loading...";
        this.saveStatusColor = 0xFFCC00;
        this.lastValidatedText = "";

        if (this.previewTexture != null) {
            this.previewTexture.close();
            this.previewTexture = null;
        }
        if (this.previewImage != null) {
            this.previewImage.close();
            this.previewImage = null;
        }
        this.previewTextureLocation = null;

        Path src = findSourcePath(node.relativePath);
        if (src != null) {
            if (node.relativePath.endsWith(".json")) {
                try {
                    selectedFileText = Files.readString(src);
                    saveStatusMessage = "Loaded successfully";
                    saveStatusColor = 0x55FF55;
                    this.lastValidatedText = selectedFileText;
                    runJsonLint(selectedFileText);
                } catch (Exception e) {
                    selectedFileText = "Failed to read file: " + e.getMessage();
                    saveStatusMessage = "Error reading file";
                    saveStatusColor = 0xFF5555;
                }
            } else if (node.relativePath.endsWith(".png")) {
                try (InputStream is = Files.newInputStream(src)) {
                    this.previewImage = NativeImage.read(is);
                    this.previewTexture = new DynamicTexture(this.previewImage);
                    this.previewTextureLocation = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "preview_" + System.currentTimeMillis());
                    if (this.minecraft != null) {
                        this.minecraft.getTextureManager().register(this.previewTextureLocation, this.previewTexture);
                    }
                    saveStatusMessage = "Image loaded successfully";
                    saveStatusColor = 0x55FF55;
                } catch (Exception e) {
                    saveStatusMessage = "Failed to load image: " + e.getMessage();
                    saveStatusColor = 0xFF5555;
                }
            } else {
                saveStatusMessage = "Preview not supported for this file type";
                saveStatusColor = 0x888888;
            }
        } else {
            saveStatusMessage = "File not found";
            saveStatusColor = 0xFF5555;
        }
        updateRightPanelWidgets();
    }

    private void saveEdit() {
        if (selectedNode == null || jsonEditorBox == null || this.minecraft == null) {
            return;
        }
        String textToSave = jsonEditorBox.getValue();
        String rel = selectedNode.relativePath;
        Path targetFile = resolveExportPath(rel);

        if (targetFile != null) {
            try {
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, textToSave);
                saveStatusMessage = "Saved edit successfully!";
                saveStatusColor = 0x55FF55;
                if (this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.literal("Saved and exported: " + targetFile.getFileName()),
                            false
                    );
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to save edit for {}", rel, e);
                saveStatusMessage = "Save failed: " + e.getMessage();
                saveStatusColor = 0xFF5555;
            }
        }
    }

    private void saveRename() {
        if (selectedNode == null || renameBox == null || this.minecraft == null) {
            return;
        }
        String newName = renameBox.getValue().trim();
        if (!newName.endsWith(".json")) {
            saveStatusMessage = "Invalid name (must end in .json)";
            saveStatusColor = 0xFF5555;
            return;
        }

        String rel = selectedNode.relativePath;
        String parentPath = "";
        int lastSlash = rel.lastIndexOf('/');
        if (lastSlash != -1) {
            parentPath = rel.substring(0, lastSlash + 1);
        }
        String newRelPath = parentPath + newName;
        Path targetFile = resolveExportPath(newRelPath);

        if (targetFile != null) {
            try {
                Files.createDirectories(targetFile.getParent());
                String textToSave = jsonEditorBox != null ? jsonEditorBox.getValue() : selectedFileText;
                Files.writeString(targetFile, textToSave);
                saveStatusMessage = "Renamed and exported!";
                saveStatusColor = 0x55FF55;
                if (this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.literal("Renamed and exported to: " + newName),
                            false
                    );
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to rename file {}", rel, e);
                saveStatusMessage = "Rename failed: " + e.getMessage();
                saveStatusColor = 0xFF5555;
            }
        }
    }

    private void openOnHost() {
        if (selectedNode == null || this.minecraft == null) {
            return;
        }
        String rel = selectedNode.relativePath;
        Path targetFile = resolveExportPath(rel);
        if (targetFile != null) {
            try {
                if (!Files.exists(targetFile)) {
                    Files.createDirectories(targetFile.getParent());
                    Path srcPath = findSourcePath(rel);
                    if (srcPath != null) {
                        try (InputStream in = Files.newInputStream(srcPath)) {
                            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                Util.getPlatform().openUri(targetFile.toUri());
                saveStatusMessage = "Opened on host machine";
                saveStatusColor = 0x55FF55;
            } catch (Exception e) {
                Constants.LOG.error("Failed to open file on host: {}", rel, e);
                saveStatusMessage = "Open failed: " + e.getMessage();
                saveStatusColor = 0xFF5555;
            }
        }
    }

    private Path resolveExportPath(String rel) {
        if (this.minecraft == null) return null;
        Path gameDir = this.minecraft.gameDirectory.toPath();
        if (rel.startsWith("assets/")) {
            String sub = rel.substring("assets/".length());
            return ExportPaths.assetsRoot(gameDir).resolve(sub);
        } else if (rel.startsWith("data/")) {
            String sub = rel.substring("data/".length());
            return ExportPaths.dataRoot(gameDir).resolve(sub);
        }
        return null;
    }

    private void selectAllFiltered() {
        if (this.resourceListWidget != null) {
            for (var entry : this.resourceListWidget.children()) {
                if (entry instanceof TreeEntry te) {
                    if (!te.node.isDirectory) {
                        selectedPaths.add(te.node.relativePath);
                    } else {
                        selectAllSubFiles(te.node);
                    }
                }
            }
        }
    }

    private void selectAllSubFiles(TreeNode node) {
        if (!node.isDirectory) {
            selectedPaths.add(node.relativePath);
        } else {
            for (TreeNode child : node.children) {
                selectAllSubFiles(child);
            }
        }
    }

    private void exportSelected() {
        if (selectedPaths.isEmpty() || this.minecraft == null) {
            return;
        }

        Set<Path> directoriesToOpen = new HashSet<>();

        for (String rel : selectedPaths) {
            Path targetFile = resolveExportPath(rel);
            if (targetFile != null) {
                try {
                    Files.createDirectories(targetFile.getParent());
                    Path srcPath = findSourcePath(rel);
                    if (srcPath != null) {
                        try (InputStream in = Files.newInputStream(srcPath)) {
                            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        }
                        directoriesToOpen.add(targetFile.getParent());
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to export resource {}", rel, e);
                }
            }
        }

        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.literal("Successfully exported " + selectedPaths.size() + " resources!"),
                    false
            );
        }

        for (Path dir : directoriesToOpen) {
            Util.getPlatform().openUri(dir.toUri());
        }

        this.onClose();
    }

    private Path findSourcePath(String rel) {
        for (Path root : mod.rootPaths()) {
            Path p = root.resolve(rel);
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.jsonEditorBox != null && this.jsonEditorBox.visible) {
            String val = this.jsonEditorBox.getValue();
            if (!val.equals(lastValidatedText)) {
                lastValidatedText = val;
                runJsonLint(val);
            }
        }
    }

    private void runJsonLint(String text) {
        if (text.trim().isEmpty()) {
            saveStatusMessage = "Empty file";
            saveStatusColor = 0x888888;
            return;
        }
        try {
            JsonParser.parseString(text);
            saveStatusMessage = "JSON Status: Valid";
            saveStatusColor = 0x55FF55;
        } catch (JsonSyntaxException e) {
            String msg = e.getMessage();
            if (msg.contains("at line")) {
                int atIdx = msg.indexOf("at line");
                msg = msg.substring(atIdx);
            }
            saveStatusMessage = "Lint Error: " + msg;
            saveStatusColor = 0xFF5555;
        } catch (Exception e) {
            saveStatusMessage = "Lint Error: " + e.getMessage();
            saveStatusColor = 0xFF5555;
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x60000000);

        guiGraphics.fill(5, 50, 185, this.height - 8, 0x90000000);
        drawPanelBorder(guiGraphics, 5, 50, 185, this.height - 8, 0xFF404040);

        guiGraphics.fill(190, 50, this.width - 10, this.height - 8, 0x90000000);
        drawPanelBorder(guiGraphics, 190, 50, this.width - 10, this.height - 8, 0xFF404040);

        guiGraphics.fill(0, 0, this.width, 45, 0x90000000);
        drawPanelBorder(guiGraphics, 0, 0, this.width, 45, 0xFF404040);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(this.font, "Packdev Toolkit Browser - " + mod.name(), 10, 8, 0xFFFFFF, true);
        guiGraphics.drawString(this.font, "Mod ID: " + mod.id() + " v" + mod.version(), 10, 22, 0x888888, true);

        if (selectedNode != null) {
            String fileNameLabel = selectedNode.name;
            guiGraphics.drawString(this.font, fileNameLabel, 195, 55, 0xFFFFFF, false);

            String pathLabel = "Path: " + selectedNode.relativePath;
            guiGraphics.drawString(this.font, pathLabel, 195, 68, 0x888888, false);

            if (selectedNode.relativePath.endsWith(".json")) {
                guiGraphics.drawString(this.font, "New Name:", 195, 86, 0xDDDDDD, false);
            }

            if (selectedNode.relativePath.endsWith(".png") && previewTextureLocation != null && previewImage != null) {
                int imgW = previewImage.getWidth();
                int imgH = previewImage.getHeight();
                int drawWidth = 64;
                int drawHeight = 64;
                if (imgW > 0 && imgH > 0) {
                    float ratio = (float) imgW / imgH;
                    int maxW = this.width - 220;
                    int maxH = this.height - 170;
                    if (imgW > maxW || imgH > maxH) {
                        if (ratio > (float) maxW / maxH) {
                            drawWidth = maxW;
                            drawHeight = Math.round(maxW / ratio);
                        } else {
                            drawHeight = maxH;
                            drawWidth = Math.round(maxH * ratio);
                        }
                    } else {
                        drawWidth = imgW;
                        drawHeight = imgH;
                    }
                }

                int imageX = 190 + (this.width - 200 - drawWidth) / 2;
                int imageY = 102 + (this.height - 152 - drawHeight) / 2;

                guiGraphics.blit(previewTextureLocation, imageX, imageY, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);

                String dimLabel = String.format("Dimensions: %dx%d", imgW, imgH);
                guiGraphics.drawCenteredString(this.font, dimLabel, 190 + (this.width - 200) / 2, imageY + drawHeight + 10, 0xAAAAAA);
            }
        } else {
            guiGraphics.drawCenteredString(this.font, "Select a file on the left to edit or preview", (190 + this.width) / 2, this.height / 2, 0x888888);
        }

        guiGraphics.drawString(this.font, saveStatusMessage, 195, this.height - 22, saveStatusColor, false);

        if (selectedNode != null && !selectedNode.isDirectory) {
            String statsText = "";
            if (selectedNode.relativePath.endsWith(".json") && jsonEditorBox != null) {
                String val = jsonEditorBox.getValue();
                int lines = val.split("\n", -1).length;
                int size = val.getBytes(StandardCharsets.UTF_8).length;
                statsText = String.format("Lines: %d | Size: %d B", lines, size);
            } else if (selectedNode.relativePath.endsWith(".png") && previewImage != null) {
                Path src = findSourcePath(selectedNode.relativePath);
                if (src != null) {
                    try {
                        long size = Files.size(src);
                        statsText = String.format("Size: %d B", size);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (!statsText.isEmpty()) {
                guiGraphics.drawString(this.font, statsText, this.width - this.font.width(statsText) - 15, this.height - 22, 0xAAAAAA, false);
            }
        }

        String counterText = String.format("Selected: %d files", selectedPaths.size());
        guiGraphics.drawString(this.font, counterText, 10, this.height - 46, 0x55FF55, false);
    }

    private void drawPanelBorder(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int color) {
        guiGraphics.fill(left, top, right, top + 1, color);
        guiGraphics.fill(left, bottom - 1, right, bottom, color);
        guiGraphics.fill(left, top + 1, left + 1, bottom - 1, color);
        guiGraphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    void renderTreeGuides(GuiGraphics guiGraphics, TreeNode node, int startX, int startY) {
        if (node.parent == null || node.parent == treeRoot) {
            return;
        }

        int color = 0xFF404040;
        int branchY = startY + 9;

        for (int depth = 1; depth <= node.depth; depth++) {
            TreeNode ancestor = getAncestorAtDepth(node, depth);
            if (ancestor == null) {
                continue;
            }

            int connectorX = startX + (depth * 12) - 8;
            boolean hasContinuation = hasFollowingSiblingAtDepth(ancestor);

            if (depth < node.depth) {
                if (hasContinuation) {
                    guiGraphics.fill(connectorX, startY - 2, connectorX + 1, startY + 16, color);
                }
                continue;
            }

            guiGraphics.fill(connectorX, startY - 2, connectorX + 1, branchY, color);
            if (hasContinuation) {
                guiGraphics.fill(connectorX, branchY, connectorX + 1, startY + 16, color);
            }
            guiGraphics.fill(connectorX, branchY, connectorX + 5, branchY + 1, color);
        }
    }

    private TreeNode getAncestorAtDepth(TreeNode node, int depth) {
        TreeNode current = node;
        while (current != null && current.depth > depth) {
            current = current.parent;
        }
        return current != null && current.depth == depth ? current : null;
    }

    private boolean hasFollowingSiblingAtDepth(TreeNode node) {
        TreeNode parentNode = node.parent;
        if (parentNode == null) {
            return false;
        }
        int index = parentNode.children.indexOf(node);
        if (index < 0) {
            return false;
        }
        return index < parentNode.children.size() - 1;
    }

    void selectRange(String fromPath, String toPath, boolean select) {
        if (this.resourceListWidget == null) return;
        int fromIdx = -1;
        int toIdx = -1;
        List<TreeEntry> children = this.resourceListWidget.children();
        for (int i = 0; i < children.size(); i++) {
            TreeNode n = children.get(i).node;
            if (n.relativePath.equals(fromPath)) {
                fromIdx = i;
            }
            if (n.relativePath.equals(toPath)) {
                toIdx = i;
            }
        }
        if (fromIdx != -1 && toIdx != -1) {
            int start = Math.min(fromIdx, toIdx);
            int end = Math.max(fromIdx, toIdx);
            for (int i = start; i <= end; i++) {
                TreeNode n = children.get(i).node;
                toggleSelection(n, select);
            }
        }
    }

    void gatherNestedFiles(TreeNode node, List<String> list) {
        if (!node.isDirectory) {
            list.add(node.relativePath);
        } else {
            for (TreeNode child : node.children) {
                gatherNestedFiles(child, list);
            }
        }
    }

    void toggleSelection(TreeNode node, boolean select) {
        if (!node.isDirectory) {
            if (select) {
                selectedPaths.add(node.relativePath);
            } else {
                selectedPaths.remove(node.relativePath);
            }
        } else {
            List<String> nestedFiles = new ArrayList<>();
            gatherNestedFiles(node, nestedFiles);
            if (select) {
                selectedPaths.addAll(nestedFiles);
            } else {
                nestedFiles.forEach(selectedPaths::remove);
            }
        }
    }

    @Override
    public void onClose() {
        if (this.previewTexture != null) {
            this.previewTexture.close();
            this.previewTexture = null;
        }
        if (this.previewImage != null) {
            this.previewImage.close();
            this.previewImage = null;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
