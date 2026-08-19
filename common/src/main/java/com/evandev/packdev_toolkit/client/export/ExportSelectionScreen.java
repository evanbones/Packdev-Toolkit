package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.config.ModConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lets the player pick which of a set of resources (textures, blockstates, models) to copy
 * out of the resource manager into the configured export directory.
 */
public class ExportSelectionScreen extends Screen {
    private final Set<ResourceLocation> availableAssets;
    private final Map<Checkbox, ResourceLocation> checkboxes = new HashMap<>();

    public ExportSelectionScreen(Set<ResourceLocation> availableAssets) {
        super(Component.literal("Select Assets to Extract"));
        this.availableAssets = availableAssets;
    }

    private static @NotNull Path getTargetFile(ResourceLocation fileResource) {
        Path assetsRoot = ExportPaths.assetsRoot(Minecraft.getInstance().gameDirectory.toPath());
        return assetsRoot.resolve(fileResource.getNamespace()).resolve(fileResource.getPath());
    }

    @Override
    protected void init() {
        checkboxes.clear();
        int y = 40;
        int x = this.width / 2 - 150;

        for (ResourceLocation asset : availableAssets) {
            Checkbox checkbox = Checkbox.builder(Component.literal(asset.toString()), this.font)
                    .pos(x, y)
                    .selected(true)
                    .build();

            this.addRenderableWidget(checkbox);
            checkboxes.put(checkbox, asset);
            y += 24;

            if (y > this.height - 60) {
                y = 40;
                x += 160;
            }
        }

        this.addRenderableWidget(Button.builder(Component.literal("Extract"), btn -> extractSelected())
                .bounds(this.width / 2 - 105, this.height - 30, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(this.width / 2 + 5, this.height - 30, 100, 20)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    private void extractSelected() {
        boolean anyExported = false;

        for (Map.Entry<Checkbox, ResourceLocation> entry : checkboxes.entrySet()) {
            if (entry.getKey().selected()) {
                Path targetDir = copyAsset(entry.getValue());
                if (targetDir != null) {
                    anyExported = true;
                }
            }
        }

        if (anyExported && ModConfig.get().openFolderOnExport) {
            Path exportRoot = ExportPaths.assetsRoot(Minecraft.getInstance().gameDirectory.toPath()).getParent();
            Util.getPlatform().openUri(exportRoot.toUri());
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
}
