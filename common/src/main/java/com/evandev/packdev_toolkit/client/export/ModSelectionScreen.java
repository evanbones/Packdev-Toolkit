package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.platform.ModJarInfo;
import com.evandev.packdev_toolkit.platform.Services;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ModSelectionScreen extends Screen {
    private static final ResourceLocation DEFAULT_MOD_ICON = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/icons/icon.png");
    private final Map<String, ResourceLocation> modIconCache = new HashMap<>();
    private ModListWidget modListWidget;
    private List<ModJarInfo> allMods;

    public ModSelectionScreen() {
        super(Component.literal("Packdev Toolkit - Select Mod"));
    }

    @Override
    protected void init() {
        this.allMods = Services.PLATFORM.getLoadedMods();

        EditBox searchBox = new EditBox(this.font, this.width - 190, 13, 180, 18, Component.literal("Search Mods"));
        searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(searchBox);

        this.modListWidget = new ModListWidget(this.minecraft, this.width - 16, this.height - 90, 53, 32);
        this.modListWidget.setX(8);
        this.addRenderableWidget(this.modListWidget);

        this.onSearchChanged(searchBox.getValue());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 25, 100, 18)
                .build());
    }

    private void onSearchChanged(String text) {
        if (this.modListWidget != null) {
            this.modListWidget.updateFilter(text);
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x60000000);

        guiGraphics.fill(5, 50, this.width - 10, this.height - 35, 0x90000000);
        drawPanelBorder(guiGraphics, 5, 50, this.width - 10, this.height - 35, 0xFF404040);

        guiGraphics.fill(0, 0, this.width, 45, 0x90000000);
        drawPanelBorder(guiGraphics, 0, 0, this.width, 45, 0xFF404040);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(this.font, "Packdev Toolkit - Select Mod", 10, 8, 0xFFFFFF, true);
        guiGraphics.drawString(this.font, "Choose a loaded mod jar to browse and export resources", 10, 22, 0x888888, true);
    }

    private void drawPanelBorder(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int color) {
        guiGraphics.fill(left, top, right, top + 1, color);
        guiGraphics.fill(left, bottom - 1, right, bottom, color);
        guiGraphics.fill(left, top + 1, left + 1, bottom - 1, color);
        guiGraphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : modIconCache.values()) {
            if (loc != null && !loc.equals(DEFAULT_MOD_ICON)) {
                var tex = mc.getTextureManager().getTexture(loc);
                try {
                    ((AutoCloseable) tex).close();
                } catch (Exception ignored) {
                }
            }
        }
        modIconCache.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ResourceLocation getModIconTexture(ModJarInfo mod) {
        if (modIconCache.containsKey(mod.id())) {
            return modIconCache.get(mod.id());
        }

        Path iconPath = findModIconPath(mod);
        if (iconPath != null) {
            try (InputStream is = Files.newInputStream(iconPath)) {
                NativeImage nativeImage = NativeImage.read(is);
                DynamicTexture texture = new DynamicTexture(nativeImage);
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "mod_icon_" + mod.id().toLowerCase(Locale.ROOT));
                Minecraft.getInstance().getTextureManager().register(loc, texture);
                modIconCache.put(mod.id(), loc);
                return loc;
            } catch (Exception ignored) {
            }
        }

        modIconCache.put(mod.id(), DEFAULT_MOD_ICON);
        return DEFAULT_MOD_ICON;
    }

    private Path findModIconPath(ModJarInfo mod) {
        for (Path root : mod.rootPaths()) {
            Path icon = root.resolve("icon.png");
            if (Files.exists(icon)) return icon;

            Path assetsIcon = root.resolve("assets/" + mod.id() + "/icon.png");
            if (Files.exists(assetsIcon)) return assetsIcon;

            Path logo = root.resolve("logo.png");
            if (Files.exists(logo)) return logo;

            Path pack = root.resolve("pack.png");
            if (Files.exists(pack)) return pack;
        }
        return null;
    }

    class ModListWidget extends ObjectSelectionList<ModListWidget.ModEntry> {
        public ModListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.width - 6;
        }

        public void updateFilter(String query) {
            this.clearEntries();
            String lower = query.toLowerCase(Locale.ROOT);
            for (ModJarInfo mod : allMods) {
                if (mod.name().toLowerCase(Locale.ROOT).contains(lower)
                        || mod.id().toLowerCase(Locale.ROOT).contains(lower)) {
                    this.addEntry(new ModEntry(mod));
                }
            }
        }

        class ModEntry extends ObjectSelectionList.Entry<ModEntry> {
            private final ModJarInfo mod;

            public ModEntry(ModJarInfo mod) {
                this.mod = mod;
            }

            @Override
            public @NotNull Component getNarration() {
                return Component.literal(mod.name());
            }

            @Override
            public void render(@NotNull GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
                if (isHovered) {
                    guiGraphics.fill(left, top, left + width, top + height, 0x15FFFFFF);
                }

                ResourceLocation iconLoc = getModIconTexture(mod);
                guiGraphics.blit(iconLoc, left + 5, top + 5, 0, 0, 22, 22, 22, 22);

                guiGraphics.drawString(ModSelectionScreen.this.font, mod.name(), left + 35, top + 4, 0xFFFFFF, false);

                String details = mod.id() + " v" + mod.version();
                guiGraphics.drawString(ModSelectionScreen.this.font, details, left + 35, top + 16, 0x888888, false);

                guiGraphics.fill(left, top + height - 1, left + width, top + height, 0x1AFFFFFF);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (ModSelectionScreen.this.minecraft != null) {
                    ModSelectionScreen.this.minecraft.setScreen(new PackBrowserScreen(ModSelectionScreen.this, mod));
                    return true;
                }
                return false;
            }
        }
    }
}
