package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.client.gui.GuiUtil;
import com.evandev.packdev_toolkit.client.gui.ModEditBox;
import com.evandev.packdev_toolkit.config.ModConfig;
import com.evandev.packdev_toolkit.platform.ModJarInfo;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
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
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

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
    private static final int GAP = 6;
    private static final int HEADER_HEIGHT = 24;
    private static final String NAME_LABEL = "Name";
    private static final int MIN_LEFT_WIDTH = 150;
    private static final int MIN_RENAME_BOX_WIDTH = 40;
    // Kept between visits so a dragged divider stays where it was put
    private static int chosenLeftWidth = -1;
    private static long resizeCursor = 0L;
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
    private int saveStatusColor = GuiUtil.STATUS_NEUTRAL;
    private long selectedFileSize = -1;
    private String lastValidatedText = "";
    private ResourceLocation previewTextureLocation = null;
    private DynamicTexture previewTexture = null;
    private NativeImage previewImage = null;

    private JsonEditorBox jsonEditorBox;
    private EditBox renameBox;
    private Button saveRenameBtn;
    private Button saveEditBtn;
    private Button openOnHostBtn;

    private int[] leftPanel;
    private int[] rightPanel;
    private int rightInnerX;
    private int rightInnerY;
    private int rightInnerW;
    private int footerY;
    private int counterY;
    private boolean draggingDivider = false;
    private int dividerGrabOffset;
    private boolean showingResizeCursor = false;

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

    private void computeLayout() {
        int pad = GuiUtil.PANEL_PADDING;
        int contentY = GuiUtil.TOP_BAR_HEIGHT + GAP;
        int panelH = this.height - contentY - GAP;
        int leftW = clampLeftWidth(chosenLeftWidth > 0 ? chosenLeftWidth : (int) (this.width * 0.4));

        leftPanel = new int[]{GAP, contentY, leftW, panelH};
        rightPanel = new int[]{GAP * 2 + leftW, contentY, this.width - leftW - GAP * 3, panelH};

        rightInnerX = rightPanel[0] + pad;
        rightInnerY = rightPanel[1] + pad;
        rightInnerW = rightPanel[2] - pad * 2;
        footerY = rightPanel[1] + rightPanel[3] - pad - 9 - GuiUtil.TEXT_INSET;
    }

    private int clampLeftWidth(int width) {
        return Mth.clamp(width, MIN_LEFT_WIDTH, Math.max(MIN_LEFT_WIDTH, this.width - GAP * 3 - minRightWidth()));
    }

    /**
     * Just enough for the rename row: the label, a usable text box and the Rename and Save buttons.
     */
    private int minRightWidth() {
        int rowWidth = GuiUtil.TEXT_INSET + this.font.width(NAME_LABEL) + 5 + MIN_RENAME_BOX_WIDTH + 4 + 50 + 2 + 40;
        return rowWidth + GuiUtil.PANEL_PADDING * 2;
    }

    /**
     * The gap between the panels, plus a couple of pixels of each panel's edge so it isn't fiddly to grab.
     */
    private boolean isOverDivider(double mouseX, double mouseY) {
        int dividerX = leftPanel[0] + leftPanel[2];
        return mouseX >= dividerX - 2 && mouseX < rightPanel[0] + 2
                && mouseY >= leftPanel[1] && mouseY < leftPanel[1] + leftPanel[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverDivider(mouseX, mouseY)) {
            this.draggingDivider = true;
            this.dividerGrabOffset = (int) mouseX - (leftPanel[0] + leftPanel[2]);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingDivider) {
            int newWidth = clampLeftWidth((int) mouseX - this.dividerGrabOffset - leftPanel[0]);
            if (newWidth != leftPanel[2]) {
                chosenLeftWidth = newWidth;
                this.rebuildWidgets();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingDivider && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.draggingDivider = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateCursor(boolean resize) {
        if (resize == this.showingResizeCursor || this.minecraft == null) return;
        this.showingResizeCursor = resize;
        if (resize && resizeCursor == 0L) {
            resizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
        }
        GLFW.glfwSetCursor(this.minecraft.getWindow().getWindow(), resize ? resizeCursor : 0L);
    }

    @Override
    public void removed() {
        updateCursor(false);
        super.removed();
    }

    @Override
    protected void init() {
        // Widgets are rebuilt on resize, so carry over anything typed into the old ones
        String previousSearch = this.searchBox != null ? this.searchBox.getValue() : "";
        String previousName = this.renameBox != null ? this.renameBox.getValue() : this.selectedFileName;
        double previousListScroll = this.resourceListWidget != null ? this.resourceListWidget.getScrollAmount() : 0;
        double previousListScrollX = this.resourceListWidget != null ? this.resourceListWidget.getScrollX() : 0;
        double previousEditorScroll = 0;
        if (this.jsonEditorBox != null && this.jsonEditorBox.visible) {
            this.selectedFileText = this.jsonEditorBox.getValue();
            previousEditorScroll = this.jsonEditorBox.getScroll();
        }
        computeLayout();
        int pad = GuiUtil.PANEL_PADDING;

        int leftInnerX = leftPanel[0] + pad;
        int leftInnerY = leftPanel[1] + pad;
        int leftInnerW = leftPanel[2] - pad * 2;
        int leftBottom = leftPanel[1] + leftPanel[3] - pad;

        this.searchBox = new ModEditBox(this.font, leftInnerX, leftInnerY, leftInnerW, 16, Component.literal("Search files"));
        this.searchBox.setHint(Component.literal("Search files...").withStyle(ChatFormatting.GRAY));
        this.searchBox.setValue(previousSearch);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        int buttonsY = leftBottom - 20;
        int listY = leftInnerY + 20;
        int listBottom = buttonsY - 16;
        this.counterY = listBottom + 4;
        this.resourceListWidget = new ResourceListWidget(this, leftInnerX, listY, leftInnerW, listBottom - listY, 18);
        this.addRenderableWidget(this.resourceListWidget);

        int buttonW = (leftInnerW - 4) / 3;
        this.addRenderableWidget(Button.builder(Component.literal("Export"), btn -> exportSelected())
                .bounds(leftInnerX, buttonsY, buttonW, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Select All"), btn -> selectAllFiltered())
                .bounds(leftInnerX + buttonW + 2, buttonsY, buttonW, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(leftInnerX + (buttonW + 2) * 2, buttonsY, leftInnerW - (buttonW + 2) * 2, 20).build());

        int rightEdge = rightInnerX + rightInnerW;
        this.openOnHostBtn = Button.builder(Component.literal("Open"), btn -> openOnHost())
                .bounds(rightEdge - 44, rightInnerY, 44, 20)
                .build();
        this.addRenderableWidget(this.openOnHostBtn);

        int rowY = rightInnerY + HEADER_HEIGHT;
        int saveX = rightEdge - 40;
        int renameX = saveX - 2 - 50;
        int nameX = rightInnerX + GuiUtil.TEXT_INSET + this.font.width(NAME_LABEL) + 5;
        this.renameBox = new ModEditBox(this.font, nameX, rowY + 2, renameX - 4 - nameX, 16, Component.literal("New Name"));
        this.addRenderableWidget(this.renameBox);

        this.saveRenameBtn = Button.builder(Component.literal("Rename"), btn -> saveRename())
                .bounds(renameX, rowY, 50, 20)
                .build();
        this.addRenderableWidget(this.saveRenameBtn);

        this.saveEditBtn = Button.builder(Component.literal("Save"), btn -> saveEdit())
                .bounds(saveX, rowY, 40, 20)
                .build();
        this.addRenderableWidget(this.saveEditBtn);

        int editorY = rowY + 24;
        // The box's bounds leave out its scrollbar, which sits to the right as in the file list
        int editorW = rightInnerW - GuiUtil.SCROLLBAR_GAP - GuiUtil.SCROLLBAR_WIDTH;
        this.jsonEditorBox = new JsonEditorBox(this.font, rightInnerX, editorY, editorW, footerY - 5 - editorY);
        this.addRenderableWidget(this.jsonEditorBox);

        updateRightPanelWidgets();
        this.renameBox.setValue(previousName);
        this.jsonEditorBox.setScroll(previousEditorScroll);
        onSearchChanged(this.searchBox.getValue());
        this.resourceListWidget.setScroll(previousListScroll, previousListScrollX);
    }

    /**
     * Swaps vanilla's bare scroller, drawn hanging off the box's right edge, for the same track and thumb as the
     * file list.
     */
    private static class JsonEditorBox extends MultiLineEditBox {
        private boolean draggingScrollbar = false;

        JsonEditorBox(Font font, int x, int y, int width, int height) {
            super(font, x, y, width, height, Component.literal("Select a file to edit"), Component.literal("JSON Editor"));
        }

        double getScroll() {
            return this.scrollAmount();
        }

        void setScroll(double scroll) {
            this.setScrollAmount(scroll);
        }

        private int scrollbarX() {
            return this.getX() + this.width + GuiUtil.SCROLLBAR_GAP;
        }

        private boolean isOverScrollbar(double mouseX, double mouseY) {
            return this.visible && mouseX >= scrollbarX() && mouseX < scrollbarX() + GuiUtil.SCROLLBAR_WIDTH
                    && mouseY >= this.getY() && mouseY < this.getY() + this.height;
        }

        private void scrollToMouse(double mouseY) {
            this.setScrollAmount(GuiUtil.scrollAmountFromMouse(mouseY, this.getY(), this.height, this.getMaxScrollAmount()));
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            // So the wheel scrolls the editor over its scrollbar too
            return super.isMouseOver(mouseX, mouseY) || isOverScrollbar(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverScrollbar(mouseX, mouseY)) {
                this.draggingScrollbar = true;
                scrollToMouse(mouseY);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.draggingScrollbar && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                scrollToMouse(mouseY);
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.draggingScrollbar = false;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        protected void renderDecorations(@NotNull GuiGraphics guiGraphics) {
            GuiUtil.drawScrollbar(guiGraphics, scrollbarX(), this.getY(), this.height, this.scrollAmount(), this.getMaxScrollAmount());
        }

        @Override
        protected void renderBorder(@NotNull GuiGraphics guiGraphics, int x, int y, int width, int height) {
            GuiUtil.blitSprite(guiGraphics, this.isFocused() ? GuiUtil.TEXT_FIELD_HIGHLIGHTED : GuiUtil.TEXT_FIELD, x, y, width, height);
        }
    }

    TreeNode getSelectedNode() {
        return this.selectedNode;
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
            this.openOnHostBtn.visible = selectedNode != null && !selectedNode.isDirectory;
        }
    }

    void selectFile(TreeNode node) {
        this.selectedNode = node;
        this.selectedFileName = node.name;
        this.selectedFileText = "";
        this.saveStatusMessage = "Loading...";
        this.saveStatusColor = GuiUtil.STATUS_PENDING;
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
        this.selectedFileSize = -1;

        Path src = findSourcePath(node.relativePath);
        if (src != null) {
            try {
                this.selectedFileSize = Files.size(src);
            } catch (Exception ignored) {
            }

            if (node.relativePath.endsWith(".json")) {
                try {
                    selectedFileText = Files.readString(src);
                    saveStatusMessage = "Loaded";
                    saveStatusColor = GuiUtil.STATUS_OK;
                    this.lastValidatedText = selectedFileText;
                    runJsonLint(selectedFileText);
                } catch (Exception e) {
                    selectedFileText = "Failed to read file: " + e.getMessage();
                    saveStatusMessage = "Error reading file";
                    saveStatusColor = GuiUtil.STATUS_ERROR;
                }
            } else if (node.relativePath.endsWith(".png")) {
                try (InputStream is = Files.newInputStream(src)) {
                    this.previewImage = NativeImage.read(is);
                    this.previewTexture = new DynamicTexture(this.previewImage);
                    this.previewTextureLocation = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "preview_" + System.currentTimeMillis());
                    if (this.minecraft != null) {
                        this.minecraft.getTextureManager().register(this.previewTextureLocation, this.previewTexture);
                    }
                    saveStatusMessage = "Image loaded";
                    saveStatusColor = GuiUtil.STATUS_OK;
                } catch (Exception e) {
                    saveStatusMessage = "Failed to load image: " + e.getMessage();
                    saveStatusColor = GuiUtil.STATUS_ERROR;
                }
            } else {
                saveStatusMessage = "Preview not supported for this file type";
                saveStatusColor = GuiUtil.STATUS_NEUTRAL;
            }
        } else {
            saveStatusMessage = "File not found";
            saveStatusColor = GuiUtil.STATUS_ERROR;
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
                saveStatusColor = GuiUtil.STATUS_OK;
                if (this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.literal("Saved and exported: " + targetFile.getFileName()),
                            false
                    );
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to save edit for {}", rel, e);
                saveStatusMessage = "Save failed: " + e.getMessage();
                saveStatusColor = GuiUtil.STATUS_ERROR;
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
            saveStatusColor = GuiUtil.STATUS_ERROR;
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
                saveStatusColor = GuiUtil.STATUS_OK;
                if (this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.literal("Renamed and exported to: " + newName),
                            false
                    );
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to rename file {}", rel, e);
                saveStatusMessage = "Rename failed: " + e.getMessage();
                saveStatusColor = GuiUtil.STATUS_ERROR;
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
                saveStatusColor = GuiUtil.STATUS_OK;
            } catch (Exception e) {
                Constants.LOG.error("Failed to open file on host: {}", rel, e);
                saveStatusMessage = "Open failed: " + e.getMessage();
                saveStatusColor = GuiUtil.STATUS_ERROR;
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

        Set<Path> exportedAssetDirs = new HashSet<>();
        Set<Path> exportedDataDirs = new HashSet<>();

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
                        if (rel.startsWith("assets/")) {
                            exportedAssetDirs.add(targetFile.getParent());
                        } else if (rel.startsWith("data/")) {
                            exportedDataDirs.add(targetFile.getParent());
                        }
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

        if (ModConfig.get().openFolderOnExport) {
            if (!exportedAssetDirs.isEmpty()) {
                Path exportRoot = ExportPaths.assetsRoot(this.minecraft.gameDirectory.toPath()).getParent();
                Util.getPlatform().openUri(ExportPaths.folderToOpen(exportedAssetDirs, exportRoot).toUri());
            }
            if (!exportedDataDirs.isEmpty()) {
                Path exportRoot = ExportPaths.dataRoot(this.minecraft.gameDirectory.toPath()).getParent();
                Util.getPlatform().openUri(ExportPaths.folderToOpen(exportedDataDirs, exportRoot).toUri());
            }
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
            saveStatusColor = GuiUtil.STATUS_NEUTRAL;
            return;
        }
        try {
            JsonParser.parseString(text);
            saveStatusMessage = "JSON Status: Valid";
            saveStatusColor = GuiUtil.STATUS_OK;
        } catch (JsonSyntaxException e) {
            String msg = e.getMessage();
            if (msg.contains("at line")) {
                int atIdx = msg.indexOf("at line");
                msg = msg.substring(atIdx);
            }
            saveStatusMessage = "Lint Error: " + msg;
            saveStatusColor = GuiUtil.STATUS_ERROR;
        } catch (Exception e) {
            saveStatusMessage = "Lint Error: " + e.getMessage();
            saveStatusColor = GuiUtil.STATUS_ERROR;
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        GuiUtil.drawTopBar(guiGraphics, this.width);
        GuiUtil.drawVanillaPanel(guiGraphics, leftPanel[0], leftPanel[1], leftPanel[2], leftPanel[3]);
        GuiUtil.drawVanillaPanel(guiGraphics, rightPanel[0], rightPanel[1], rightPanel[2], rightPanel[3]);
        renderDivider(guiGraphics, mouseX, mouseY);

        if (selectedNode != null && !isJsonSelected()) {
            int previewY = rightInnerY + HEADER_HEIGHT;
            GuiUtil.drawWell(guiGraphics, rightInnerX, previewY, rightInnerW, footerY - 5 - previewY);
        }
    }

    private void renderDivider(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean active = this.draggingDivider || isOverDivider(mouseX, mouseY);
        updateCursor(active);
        int x = leftPanel[0] + leftPanel[2] + GAP / 2 - 1;
        int top = leftPanel[1];
        int bottom = top + leftPanel[3];
        if (active) {
            guiGraphics.fill(x, top + 4, x + 2, bottom - 4, 0x80FFFFFF);
        }
        int mid = (top + bottom) / 2;
        for (int i = -1; i <= 1; i++) {
            int dotY = mid + i * 4;
            guiGraphics.fill(x, dotY - 1, x + 2, dotY + 1, active ? 0xFFFFFFFF : 0xFFA0A0A0);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int titleY = (GuiUtil.TOP_BAR_HEIGHT - 8) / 2;
        guiGraphics.drawString(this.font, mod.name(), 8, titleY, 0xFFFFFF, true);
        int detailsX = 8 + this.font.width(mod.name()) + 8;
        String details = GuiUtil.ellipsize(this.font, mod.id() + " v" + mod.version(), this.width - detailsX - 8);
        guiGraphics.drawString(this.font, details, detailsX, titleY, GuiUtil.SUBTEXT, true);

        String counterText = selectedPaths.size() == 1 ? "1 file selected" : selectedPaths.size() + " files selected";
        guiGraphics.drawString(this.font, counterText, leftPanel[0] + GuiUtil.PANEL_PADDING + GuiUtil.TEXT_INSET, counterY, GuiUtil.TEXT, true);

        if (selectedNode == null) {
            String hint = GuiUtil.ellipsize(this.font, "Select a file to edit or preview", rightInnerW);
            int hintX = rightInnerX + (rightInnerW - this.font.width(hint)) / 2;
            guiGraphics.drawString(this.font, hint, hintX, rightPanel[1] + rightPanel[3] / 2 - 4, GuiUtil.SUBTEXT, true);
            return;
        }

        int textX = rightInnerX + GuiUtil.TEXT_INSET;
        int textW = rightInnerW - GuiUtil.TEXT_INSET * 2;
        int headerTextW = textW - (openOnHostBtn.visible ? openOnHostBtn.getWidth() + 6 - GuiUtil.TEXT_INSET : 0);
        guiGraphics.drawString(this.font, GuiUtil.ellipsize(this.font, selectedNode.name, headerTextW), textX, rightInnerY + 2, GuiUtil.TEXT, true);
        String path = GuiUtil.ellipsizeStart(this.font, selectedNode.relativePath, headerTextW);
        guiGraphics.drawString(this.font, path, textX, rightInnerY + 13, GuiUtil.SUBTEXT, true);
        if (!path.equals(selectedNode.relativePath) && mouseX >= textX && mouseX < textX + headerTextW
                && mouseY >= rightInnerY + 12 && mouseY < rightInnerY + 22) {
            this.setTooltipForNextRenderPass(Component.literal(selectedNode.relativePath));
        }

        if (isJsonSelected()) {
            guiGraphics.drawString(this.font, NAME_LABEL, textX, rightInnerY + HEADER_HEIGHT + 6, GuiUtil.TEXT, true);
        } else {
            renderPreview(guiGraphics);
        }

        String statsText = getStatsText();
        int statsW = this.font.width(statsText);
        guiGraphics.drawString(this.font, statsText, textX + textW - statsW, footerY, GuiUtil.SUBTEXT, true);
        int statusW = textW - (statsW > 0 ? statsW + 8 : 0);
        guiGraphics.drawString(this.font, GuiUtil.ellipsize(this.font, saveStatusMessage, statusW), textX, footerY, saveStatusColor, true);
    }

    private boolean isJsonSelected() {
        return selectedNode != null && !selectedNode.isDirectory && selectedNode.relativePath.endsWith(".json");
    }

    private void renderPreview(GuiGraphics guiGraphics) {
        int wellY = rightInnerY + HEADER_HEIGHT;
        int wellH = footerY - 5 - wellY;
        int availW = rightInnerW - 12;
        int availH = wellH - 12;

        if (previewTextureLocation == null || previewImage == null) {
            String message = GuiUtil.ellipsize(this.font, "No preview available", availW);
            guiGraphics.drawCenteredString(this.font, message, rightInnerX + rightInnerW / 2, wellY + wellH / 2 - 4, GuiUtil.SUBTEXT);
            return;
        }

        int imgW = previewImage.getWidth();
        int imgH = previewFrameHeight();
        if (imgW <= 0 || imgH <= 0 || availW <= 0 || availH <= 0) return;

        int drawWidth;
        int drawHeight;
        if (imgW <= availW && imgH <= availH) {
            // Whole-number scaling keeps pixel art crisp
            int scale = Math.max(1, Math.min(availW / imgW, availH / imgH));
            drawWidth = imgW * scale;
            drawHeight = imgH * scale;
        } else {
            float fit = Math.min((float) availW / imgW, (float) availH / imgH);
            drawWidth = Math.max(1, Math.round(imgW * fit));
            drawHeight = Math.max(1, Math.round(imgH * fit));
        }

        int imageX = rightInnerX + (rightInnerW - drawWidth) / 2;
        int imageY = wellY + (wellH - drawHeight) / 2;

        GuiUtil.drawCheckerboard(guiGraphics, imageX, imageY, drawWidth, drawHeight, Math.max(4, Math.min(drawWidth, drawHeight) / 8));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.blit(previewTextureLocation, imageX, imageY, drawWidth, drawHeight, 0, 0, imgW, imgH, imgW, previewImage.getHeight());
        RenderSystem.disableBlend();
    }

    /**
     * Animated textures are vertical strips of square frames, so only the first frame is previewed.
     */
    private int previewFrameHeight() {
        int w = previewImage.getWidth();
        int h = previewImage.getHeight();
        boolean animated = selectedNode != null && allFilePaths.contains(selectedNode.relativePath + ".mcmeta");
        return animated && h > w && h % w == 0 ? w : h;
    }

    private String getStatsText() {
        if (isJsonSelected() && jsonEditorBox != null) {
            String val = jsonEditorBox.getValue();
            int lines = val.split("\n", -1).length;
            int size = val.getBytes(StandardCharsets.UTF_8).length;
            return String.format("%d lines | %s", lines, formatSize(size));
        }
        if (previewImage != null) {
            String stats = previewImage.getWidth() + "x" + previewImage.getHeight();
            int frames = previewImage.getHeight() / previewFrameHeight();
            if (frames > 1) {
                stats += " | " + frames + " frames";
            }
            return selectedFileSize >= 0 ? stats + " | " + formatSize(selectedFileSize) : stats;
        }
        return selectedFileSize >= 0 ? formatSize(selectedFileSize) : "";
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        return String.format("%.1f KB", bytes / 1024.0);
    }

    void renderTreeGuides(GuiGraphics guiGraphics, TreeNode node, int startX, int startY, int rowHeight) {
        if (node.parent == null || node.parent == treeRoot) {
            return;
        }

        int color = 0xFF5E5E5E;
        int branchY = startY + rowHeight / 2;

        for (int depth = 1; depth <= node.depth; depth++) {
            TreeNode ancestor = getAncestorAtDepth(node, depth);
            if (ancestor == null) {
                continue;
            }

            // Lines up with the centre of the parent's checkbox
            int connectorX = startX + depth * TreeEntry.INDENT - 6;
            boolean hasContinuation = hasFollowingSiblingAtDepth(ancestor);

            if (depth < node.depth) {
                if (hasContinuation) {
                    guiGraphics.fill(connectorX, startY, connectorX + 1, startY + rowHeight, color);
                }
                continue;
            }

            guiGraphics.fill(connectorX, startY, connectorX + 1, branchY, color);
            if (hasContinuation) {
                guiGraphics.fill(connectorX, branchY, connectorX + 1, startY + rowHeight, color);
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
