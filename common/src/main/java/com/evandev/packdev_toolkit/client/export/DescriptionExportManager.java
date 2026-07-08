package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.client.compat.emi.EmiExportSupport;
import com.evandev.packdev_toolkit.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class DescriptionExportManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void handleExportDescription(Minecraft mc) {
        ResolvedKey resolved = resolveKey(mc);
        if (resolved == null) {
            sendMessage(mc, "Nothing found to export description for.");
            return;
        }

        exportKey(mc, resolved.key(), resolved.namespace());
    }

    private static void exportKey(Minecraft mc, String key, String namespace) {
        try {
            Path assetsRoot = ExportPaths.assetsRoot(mc.gameDirectory.toPath());
            Path langDir = assetsRoot.resolve(namespace).resolve("lang");
            Files.createDirectories(langDir);
            Path langFile = langDir.resolve("en_us.json");

            Map<String, String> langData = new LinkedHashMap<>();
            if (Files.exists(langFile)) {
                try (FileReader reader = new FileReader(langFile.toFile())) {
                    Type type = new TypeToken<LinkedHashMap<String, String>>() {
                    }.getType();
                    Map<String, String> existing = GSON.fromJson(reader, type);
                    if (existing != null) {
                        langData.putAll(existing);
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to read existing lang file: {}", langFile, e);
                }
            }

            if (langData.containsKey(key)) {
                sendMessage(mc, "Key already exists in en_us.json: " + key);
                return;
            }

            String value = "";
            if (I18n.exists(key)) {
                value = I18n.get(key);
            }

            langData.put(key, value);

            try (FileWriter writer = new FileWriter(langFile.toFile())) {
                GSON.toJson(langData, writer);
            }

            sendMessage(mc, "Exported description key to en_us.json: " + key);
            Util.getPlatform().openUri(langFile.getParent().toUri());
        } catch (Exception e) {
            Constants.LOG.error("Failed to export description key", e);
            sendMessage(mc, "Export Failed: " + e.getMessage());
        }
    }

    private static @Nullable ResolvedKey resolveKey(Minecraft mc) {
        if (Services.PLATFORM.isModLoaded("emi")) {
            ResolvedKey emiResolved = resolveEmiHovered(mc);
            if (emiResolved != null) {
                return emiResolved;
            }
        }

        if (mc.screen == null && mc.level != null) {
            HitResult hit = mc.hitResult;
            if (hit != null) {
                if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
                    Entity entity = entityHit.getEntity();
                    EntityType<?> type = entity.getType();
                    ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    if (Services.PLATFORM.isModLoaded("item_descriptions")) {
                        String key = ItemDescriptionsBridge.getEntityKey(entity);
                        String targetNamespace = id.getNamespace().equals("minecraft") ? "item_descriptions" : id.getNamespace();
                        return new ResolvedKey(key, targetNamespace);
                    }
                    return new ResolvedKey("entity." + id.getNamespace() + "." + id.getPath() + ".description", id.getNamespace());
                } else if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(blockHit.getBlockPos()).getBlock());
                    if (Services.PLATFORM.isModLoaded("item_descriptions")) {
                        ItemStack blockStack = new ItemStack(mc.level.getBlockState(blockHit.getBlockPos()).getBlock().asItem());
                        String key = ItemDescriptionsBridge.getItemKey(blockStack);
                        String targetNamespace = id.getNamespace().equals("minecraft") ? "item_descriptions" : id.getNamespace();
                        return new ResolvedKey(key, targetNamespace);
                    }
                    return new ResolvedKey("lore." + id.getNamespace() + "." + id.getPath(), id.getNamespace());
                }
            }
        }

        if (mc.screen == null && mc.player != null) {
            ItemStack held = mc.player.getMainHandItem();
            if (!held.isEmpty()) {
                return resolveItemKey(held);
            }
        }

        return null;
    }

    private static ResolvedKey resolveItemKey(ItemStack stack) {
        if (stack.getItem() instanceof SpawnEggItem spawnEgg) {
            EntityType<?> entityType = spawnEgg.getType(stack);
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            if (Services.PLATFORM.isModLoaded("item_descriptions")) {
                String key = ItemDescriptionsBridge.getEntityTypeKey(entityType);
                String targetNamespace = id.getNamespace().equals("minecraft") ? "item_descriptions" : id.getNamespace();
                return new ResolvedKey(key, targetNamespace);
            }
            return new ResolvedKey("entity." + id.getNamespace() + "." + id.getPath() + ".description", id.getNamespace());
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (Services.PLATFORM.isModLoaded("item_descriptions")) {
            String key = ItemDescriptionsBridge.getItemKey(stack);
            String targetNamespace = id.getNamespace().equals("minecraft") ? "item_descriptions" : id.getNamespace();
            return new ResolvedKey(key, targetNamespace);
        }
        return new ResolvedKey("lore." + id.getNamespace() + "." + id.getPath(), id.getNamespace());
    }

    private static @Nullable ResolvedKey resolveEmiHovered(Minecraft mc) {
        Object tagKeyObj = EmiExportSupport.getHoveredTagKey(mc);
        if (tagKeyObj instanceof TagKey<?> tagKey) {
            ResourceLocation location = tagKey.location();
            String tagDescription = "tag." + location.getNamespace() + "." + location.getPath() + ".description";
            if (Services.PLATFORM.isModLoaded("item_descriptions")) {
                String targetNamespace = location.getNamespace().equals("minecraft") ? "item_descriptions" : location.getNamespace();
                return new ResolvedKey(tagDescription, targetNamespace);
            }
            return new ResolvedKey(tagDescription, location.getNamespace());
        }

        ItemStack stack = EmiExportSupport.getHoveredStack(mc);
        if (stack != null && !stack.isEmpty()) {
            return resolveItemKey(stack);
        }

        return null;
    }

    private static void sendMessage(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private record ResolvedKey(String key, String namespace) {
    }
}
