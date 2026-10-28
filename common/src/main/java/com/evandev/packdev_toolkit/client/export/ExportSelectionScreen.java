package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.client.gui.GuiUtil;
import com.evandev.packdev_toolkit.client.gui.ScrollListWidget;
import com.evandev.packdev_toolkit.config.ModConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ExportSelectionScreen extends Screen {
    private static final int CHECKBOX_SIZE = 10;
    private static final int THUMB_SIZE = 20;
    private static final int ROW_HEIGHT = 26;
    private static final int BUTTON_WIDTH = 80;

    private final List<AssetEntry> assets = new ArrayList<>();
    private final Set<ResourceLocation> selected = new LinkedHashSet<>();
    private AssetListWidget listWidget;
    private Button extractButton;
    private Button toggleAllButton;
    private int[] panel;

    public ExportSelectionScreen(Set<ResourceLocation> availableAssets) {
        super(Component.literal("Select Assets to Extract"));

        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        availableAssets.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(loc -> assets.add(new AssetEntry(loc, resourceManager)));

        for (AssetEntry asset : assets) {
            if (asset.found) {
                selected.add(asset.location);
            }
        }
    }

    private static @NotNull Path getTargetFile(ResourceLocation fileResource) {
        Path assetsRoot = ExportPaths.assetsRoot(Minecraft.getInstance().gameDirectory.toPath());
        return assetsRoot.resolve(fileResource.getNamespace()).resolve(fileResource.getPath());
    }

    @Override
    protected void init() {
        int panelW = Math.min(this.width - 12, 360);
        int panelX = (this.width - panelW) / 2;
        int panelY = GuiUtil.TOP_BAR_HEIGHT + 6;
        int panelH = this.height - panelY - 32;
        this.panel = new int[]{panelX, panelY, panelW, panelH};

        int pad = GuiUtil.PANEL_PADDING;
        this.listWidget = new AssetListWidget(panelX + pad, panelY + pad, panelW - pad * 2, panelH - pad * 2);
        this.addRenderableWidget(this.listWidget);

        int buttonsW = BUTTON_WIDTH * 3 + 8;
        int buttonsX = (this.width - buttonsW) / 2;
        int buttonsY = this.height - 25;

        this.toggleAllButton = this.addRenderableWidget(Button.builder(Component.empty(), btn -> toggleAll())
                .bounds(buttonsX, buttonsY, BUTTON_WIDTH, 20)
                .build());
        this.extractButton = this.addRenderableWidget(Button.builder(Component.literal("Extract"), btn -> extractSelected())
                .bounds(buttonsX + BUTTON_WIDTH + 4, buttonsY, BUTTON_WIDTH, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(buttonsX + (BUTTON_WIDTH + 4) * 2, buttonsY, BUTTON_WIDTH, 20)
                .build());

        updateButtons();
    }

    private long selectableCount() {
        return assets.stream().filter(asset -> asset.found).count();
    }

    private boolean allSelected() {
        return selected.size() == selectableCount();
    }

    private void toggleAll() {
        if (allSelected()) {
            selected.clear();
        } else {
            for (AssetEntry asset : assets) {
                if (asset.found) {
                    selected.add(asset.location);
                }
            }
        }
        updateButtons();
    }

    private void updateButtons() {
        if (this.extractButton == null) return;
        this.extractButton.active = !selected.isEmpty();
        this.toggleAllButton.active = selectableCount() > 0;
        this.toggleAllButton.setMessage(Component.literal(allSelected() && !selected.isEmpty() ? "Select None" : "Select All"));
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        GuiUtil.drawTopBar(guiGraphics, this.width);
        GuiUtil.drawVanillaPanel(guiGraphics, panel[0], panel[1], panel[2], panel[3]);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int titleY = (GuiUtil.TOP_BAR_HEIGHT - 8) / 2;
        String title = "Packdev Toolkit";
        guiGraphics.drawString(this.font, title, 8, titleY, GuiUtil.TEXT, true);

        String counter = selected.size() + " of " + assets.size() + " selected";
        int counterX = this.width - this.font.width(counter) - 8;
        guiGraphics.drawString(this.font, counter, counterX, titleY, GuiUtil.SUBTEXT, true);

        int subtitleX = 8 + this.font.width(title) + 8;
        String subtitle = GuiUtil.ellipsize(this.font, "Extract assets", counterX - subtitleX - 8);
        guiGraphics.drawString(this.font, subtitle, subtitleX, titleY, GuiUtil.SUBTEXT, true);

        if (assets.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "No assets found", this.width / 2, panel[1] + panel[3] / 2 - 4, GuiUtil.SUBTEXT);
        }
    }

    @Override
    public void removed() {
        for (AssetEntry asset : assets) {
            asset.releaseThumbnail();
        }
        super.removed();
    }

    private void extractSelected() {
        Set<Path> exportedDirs = new HashSet<>();

        for (ResourceLocation asset : selected) {
            Path targetDir = copyAsset(asset);
            if (targetDir != null) {
                exportedDirs.add(targetDir);
            }
        }

        if (!exportedDirs.isEmpty() && ModConfig.get().openFolderOnExport) {
            Path exportRoot = ExportPaths.assetsRoot(Minecraft.getInstance().gameDirectory.toPath()).getParent();
            Util.getPlatform().openUri(ExportPaths.folderToOpen(exportedDirs, exportRoot).toUri());
        }

        this.onClose();
    }

    private Path copyAsset(ResourceLocation resource) {
        Path targetFile = getTargetFile(resource);
        Path targetDir = targetFile.getParent();

        try {
            Files.createDirectories(targetDir);
            var resourceManager = Minecraft.getInstance().getResourceManager();
            var resourceOpt = resourceManager.getResource(resource);

            if (resourceOpt.isPresent()) {
                if (!Files.exists(targetFile)) {
                    try (InputStream in = resourceOpt.get().open()) {
                        Files.copy(in, targetFile);
                        Constants.LOG.info("Successfully extracted asset to {}", targetFile);
                    }
                }

                if (resource.getPath().endsWith(".png")) {
                    ResourceLocation mcmetaResource = ResourceLocation.fromNamespaceAndPath(
                            resource.getNamespace(),
                            resource.getPath() + ".mcmeta"
                    );
                    Path mcmetaTargetFile = getTargetFile(mcmetaResource);

                    if (!Files.exists(mcmetaTargetFile)) {
                        var mcmetaOpt = resourceManager.getResource(mcmetaResource);
                        if (mcmetaOpt.isPresent()) {
                            try (InputStream metaIn = mcmetaOpt.get().open()) {
                                Files.copy(metaIn, mcmetaTargetFile);
                                Constants.LOG.info("Successfully extracted mcmeta to {}", mcmetaTargetFile);
                            }
                        }
                    }
                }

                return targetDir;
            } else {
                Constants.LOG.warn("Could not find the resource for {}", resource);
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to extract asset: {}", targetFile, e);
        }
        return null;
    }

    private static class AssetEntry {
        final ResourceLocation location;
        final String fileName;
        final String folder;
        final boolean found;
        ResourceLocation thumbnail;
        int imageWidth;
        int imageHeight;

        AssetEntry(ResourceLocation location, ResourceManager resourceManager) {
            this.location = location;
            String path = location.getPath();
            int slash = path.lastIndexOf('/');
            this.fileName = slash >= 0 ? path.substring(slash + 1) : path;
            this.folder = location.getNamespace() + ":" + (slash >= 0 ? path.substring(0, slash) : "");

            var resource = resourceManager.getResource(location);
            this.found = resource.isPresent();
            if (found && path.endsWith(".png")) {
                // Loaded into our own texture so we know its size and can free it when the screen closes
                try (InputStream in = resource.get().open()) {
                    NativeImage image = NativeImage.read(in);
                    this.imageWidth = image.getWidth();
                    this.imageHeight = image.getHeight();
                    DynamicTexture texture = new DynamicTexture(image);
                    this.thumbnail = Minecraft.getInstance().getTextureManager().register("packdev_toolkit_export_thumb", texture);
                } catch (Exception e) {
                    Constants.LOG.warn("Failed to load preview for {}", location, e);
                }
            }
        }

        void releaseThumbnail() {
            if (thumbnail != null) {
                Minecraft.getInstance().getTextureManager().release(thumbnail);
                thumbnail = null;
            }
        }
    }

    class AssetListWidget extends ScrollListWidget<AssetListWidget.Row> {
        AssetListWidget(int x, int y, int width, int height) {
            super(x, y, width, height, ROW_HEIGHT);
            for (AssetEntry asset : assets) {
                this.addEntry(new Row(asset));
            }
        }

        class Row implements ScrollListWidget.Entry {
            private final AssetEntry asset;

            Row(AssetEntry asset) {
                this.asset = asset;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered) {
                boolean isSelected = selected.contains(asset.location);
                if (isHovered && asset.found) {
                    guiGraphics.fill(left, top, left + width, top + height, GuiUtil.ROW_HOVER);
                }

                int cbX = left + 5;
                GuiUtil.drawCheckbox(guiGraphics, cbX, top + (height - CHECKBOX_SIZE) / 2, CHECKBOX_SIZE, isSelected, false);

                int thumbX = cbX + CHECKBOX_SIZE + 6;
                int thumbY = top + (height - THUMB_SIZE) / 2;
                renderThumbnail(guiGraphics, thumbX, thumbY);

                Font font = ExportSelectionScreen.this.font;
                int textX = thumbX + THUMB_SIZE + 6;
                int textW = left + width - textX - 4;

                String size = asset.thumbnail != null ? asset.imageWidth + "x" + asset.imageHeight : "";
                int sizeW = size.isEmpty() ? 0 : font.width(size) + 6;

                String name = GuiUtil.ellipsize(font, asset.fileName, textW - sizeW);
                guiGraphics.drawString(font, name, textX, top + 4, asset.found ? GuiUtil.TEXT : GuiUtil.SUBTEXT, true);
                if (!size.isEmpty()) {
                    guiGraphics.drawString(font, size, left + width - 4 - font.width(size), top + 4, GuiUtil.SUBTEXT, false);
                }

                if (asset.found) {
                    guiGraphics.drawString(font, GuiUtil.ellipsizeStart(font, asset.folder, textW), textX, top + 15, GuiUtil.SUBTEXT, false);
                } else {
                    guiGraphics.drawString(font, GuiUtil.ellipsize(font, "Not found in loaded resources", textW), textX, top + 15, GuiUtil.STATUS_ERROR, false);
                }

                guiGraphics.fill(left + 2, top + height - 1, left + width - 2, top + height, 0x18FFFFFF);

                if (isHovered && (!name.equals(asset.fileName) || font.width(asset.folder) > textW)) {
                    ExportSelectionScreen.this.setTooltipForNextRenderPass(Component.literal(asset.location.toString()));
                }
            }

            private void renderThumbnail(GuiGraphics guiGraphics, int x, int y) {
                if (asset.thumbnail == null) {
                    ResourceLocation icon = asset.fileName.endsWith(".json") ? PackBrowserScreen.JSON_ICON : PackBrowserScreen.FILE_ICON;
                    RenderSystem.enableBlend();
                    guiGraphics.blit(icon, x + 2, y + 2, 0, 0, 16, 16, 16, 16);
                    RenderSystem.disableBlend();
                    return;
                }

                GuiUtil.drawCheckerboard(guiGraphics, x, y, THUMB_SIZE, THUMB_SIZE, 5);
                // Animation strips are taller than wide, so only the first square frame is shown
                int frameHeight = Math.min(asset.imageHeight, asset.imageWidth);
                // Wide textures like entity skins keep their aspect ratio
                int drawHeight = Math.max(1, THUMB_SIZE * frameHeight / asset.imageWidth);
                RenderSystem.enableBlend();
                guiGraphics.blit(asset.thumbnail, x, y + (THUMB_SIZE - drawHeight) / 2, THUMB_SIZE, drawHeight,
                        0, 0, asset.imageWidth, frameHeight, asset.imageWidth, asset.imageHeight);
                RenderSystem.disableBlend();
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (!asset.found) return false;
                if (!selected.remove(asset.location)) {
                    selected.add(asset.location);
                }
                updateButtons();
                return true;
            }
        }
    }
}
