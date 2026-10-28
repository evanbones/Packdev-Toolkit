package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.client.gui.GuiUtil;
import com.evandev.packdev_toolkit.client.gui.ModEditBox;
import com.evandev.packdev_toolkit.client.gui.ScrollListWidget;
import com.evandev.packdev_toolkit.platform.ModJarInfo;
import com.evandev.packdev_toolkit.platform.Services;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    private int[] panel;

    public ModSelectionScreen() {
        super(Component.literal("Packdev Toolkit - Select Mod"));
    }

    @Override
    protected void init() {
        this.allMods = Services.PLATFORM.getLoadedMods();

        int searchW = Math.min(160, this.width / 3);
        EditBox searchBox = new ModEditBox(this.font, this.width - searchW - 6, 6, searchW, 16, Component.literal("Search Mods"));
        searchBox.setHint(Component.literal("Search mods...").withStyle(ChatFormatting.GRAY));
        searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(searchBox);

        int panelW = Math.min(this.width - 12, 360);
        int panelX = (this.width - panelW) / 2;
        int panelY = GuiUtil.TOP_BAR_HEIGHT + 6;
        int panelH = this.height - panelY - 32;
        this.panel = new int[]{panelX, panelY, panelW, panelH};

        int pad = GuiUtil.PANEL_PADDING;
        this.modListWidget = new ModListWidget(panelX + pad, panelY + pad, panelW - pad * 2, panelH - pad * 2, 30);
        this.addRenderableWidget(this.modListWidget);

        this.onSearchChanged(searchBox.getValue());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 25, 100, 20)
                .build());
    }

    private void onSearchChanged(String text) {
        if (this.modListWidget != null) {
            this.modListWidget.updateFilter(text);
        }
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
        guiGraphics.drawString(this.font, title, 8, titleY, 0xFFFFFF, true);
        int subtitleX = 8 + this.font.width(title) + 8;
        int subtitleW = this.width - Math.min(160, this.width / 3) - 12 - subtitleX - 8;
        guiGraphics.drawString(this.font, GuiUtil.ellipsize(this.font, "Select a mod to browse", subtitleW), subtitleX, titleY, GuiUtil.SUBTEXT, true);
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

    class ModListWidget extends ScrollListWidget<ModListWidget.ModEntry> {
        public ModListWidget(int x, int y, int width, int height, int itemHeight) {
            super(x, y, width, height, itemHeight);
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
            this.clampScroll();
        }

        class ModEntry implements ScrollListWidget.Entry {
            private final ModJarInfo mod;

            public ModEntry(ModJarInfo mod) {
                this.mod = mod;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered) {
                if (isHovered) {
                    guiGraphics.fill(left, top, left + width, top + height, GuiUtil.ROW_HOVER);
                }

                ResourceLocation iconLoc = getModIconTexture(mod);
                RenderSystem.enableBlend();
                guiGraphics.blit(iconLoc, left + 4, top + 4, 0, 0, 22, 22, 22, 22);
                RenderSystem.disableBlend();

                Font font = ModSelectionScreen.this.font;
                int textX = left + 32;
                int textW = width - 36;
                guiGraphics.drawString(font, GuiUtil.ellipsize(font, mod.name(), textW), textX, top + 6, 0xFFFFFF, true);
                String details = mod.id() + " v" + mod.version();
                guiGraphics.drawString(font, GuiUtil.ellipsize(font, details, textW), textX, top + 17, GuiUtil.SUBTEXT, false);

                guiGraphics.fill(left + 2, top + height - 1, left + width - 2, top + height, 0x18FFFFFF);
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
