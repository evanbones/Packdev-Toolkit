package com.evandev.packdev_toolkit.command;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.client.export.ExportPaths;
import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.material.Fluid;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagAddCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private TagAddCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("packdev")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("tag")
                        .then(Commands.literal("add")
                                .then(Commands.argument("registry", ResourceLocationArgument.id())
                                        .suggests(ExportCommand::suggestRegistries)
                                        .then(Commands.argument("tag", ResourceLocationArgument.id())
                                                .suggests(ExportCommand::suggestTags)
                                                .then(Commands.literal("hand")
                                                        .executes(context -> addTag(context, AddSource.HAND)))
                                                .then(Commands.literal("hotbar")
                                                        .executes(context -> addTag(context, AddSource.HOTBAR)))
                                                .then(Commands.literal("inventory")
                                                        .executes(context -> addTag(context, AddSource.INVENTORY)))
                                                .then(Commands.argument("entry", ResourceLocationArgument.id())
                                                        .suggests(ExportCommand::suggestRegistryEntries)
                                                        .executes(context -> addTag(context, AddSource.DIRECT)))
                                        )
                                )
                        )
                )
        );
    }

    private static int addTag(CommandContext<CommandSourceStack> context, AddSource source) {
        CommandSourceStack commandSource = context.getSource();
        try {
            ResourceLocation registryId = ResourceLocationArgument.getId(context, "registry");
            ResourceLocation tagId = ResourceLocationArgument.getId(context, "tag");

            ResourceKey<? extends Registry<Object>> registryKey = ResourceKey.createRegistryKey(registryId);
            Registry<Object> registry = commandSource.registryAccess().registry(registryKey)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown registry: " + registryId));

            List<ResourceLocation> entriesToAdd = new ArrayList<>();
            if (source == AddSource.DIRECT) {
                ResourceLocation entryId = ResourceLocationArgument.getId(context, "entry");
                if (!registry.containsKey(entryId)) {
                    commandSource.sendFailure(Component.literal("Entry " + entryId + " not found in registry " + registryId));
                    return 0;
                }
                entriesToAdd.add(entryId);
            } else {
                boolean isShortcutSupported = registryId.equals(Registries.ITEM.location())
                        || registryId.equals(Registries.BLOCK.location())
                        || registryId.equals(Registries.ENTITY_TYPE.location())
                        || registryId.equals(Registries.FLUID.location());

                if (!isShortcutSupported) {
                    commandSource.sendFailure(Component.literal("Registry " + registryId + " does not support item-based shortcuts (hand, hotbar, inventory). Please specify the entry ID directly."));
                    return 0;
                }

                ServerPlayer player = commandSource.getPlayerOrException();
                List<ItemStack> itemStacks = new ArrayList<>();
                if (source == AddSource.HAND) {
                    ItemStack stack = player.getMainHandItem();
                    if (stack.isEmpty()) {
                        commandSource.sendFailure(Component.literal("Hand is empty"));
                        return 0;
                    }
                    itemStacks.add(stack);
                } else if (source == AddSource.HOTBAR) {
                    Inventory inventory = player.getInventory();
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = inventory.getItem(i);
                        if (!stack.isEmpty()) {
                            itemStacks.add(stack);
                        }
                    }
                    ItemStack offhand = inventory.offhand.getFirst();
                    if (!offhand.isEmpty()) {
                        itemStacks.add(offhand);
                    }
                } else if (source == AddSource.INVENTORY) {
                    Inventory inventory = player.getInventory();
                    for (int i = 0; i < inventory.getContainerSize(); i++) {
                        ItemStack stack = inventory.getItem(i);
                        if (!stack.isEmpty()) {
                            itemStacks.add(stack);
                        }
                    }
                }

                for (ItemStack stack : itemStacks) {
                    List<ResourceLocation> ids = getIdsFromStack(stack, registryId);
                    entriesToAdd.addAll(ids);
                }

                if (entriesToAdd.isEmpty()) {
                    commandSource.sendFailure(Component.literal("No matching items/entries found in " + source.name().toLowerCase() + " for registry " + registryId));
                    return 0;
                }
            }

            Path dataRoot = ExportPaths.dataRoot(commandSource.getServer().getServerDirectory());
            String tagNamespace = tagId.getNamespace();
            String tagPath = tagId.getPath();
            String tagDir = getRegistryTagFolder(registryKey);

            Path tagFileDir = dataRoot.resolve(tagNamespace).resolve(tagDir);
            Files.createDirectories(tagFileDir);
            Path tagFilePath = tagFileDir.resolve(tagPath + ".json");

            JsonObject tagJson = new JsonObject();
            JsonArray valuesArray = new JsonArray();

            if (Files.exists(tagFilePath)) {
                try (FileReader reader = new FileReader(tagFilePath.toFile())) {
                    JsonObject existingJson = GSON.fromJson(reader, JsonObject.class);
                    if (existingJson != null) {
                        tagJson = existingJson;
                        if (tagJson.has("values")) {
                            valuesArray = tagJson.getAsJsonArray("values");
                        } else {
                            tagJson.add("values", valuesArray);
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to read existing tag file: {}", tagFilePath, e);
                }
            }

            tagJson.addProperty("replace", false);
            if (!tagJson.has("values")) {
                tagJson.add("values", valuesArray);
            }

            Set<String> existingIds = new HashSet<>();
            for (int i = 0; i < valuesArray.size(); i++) {
                JsonElement el = valuesArray.get(i);
                if (el.isJsonPrimitive()) {
                    existingIds.add(el.getAsString());
                } else if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("id")) {
                        existingIds.add(obj.get("id").getAsString());
                    }
                }
            }

            int addedCount = 0;
            List<String> addedList = new ArrayList<>();
            for (ResourceLocation entry : entriesToAdd) {
                String entryStr = entry.toString();
                if (!existingIds.contains(entryStr)) {
                    valuesArray.add(entryStr);
                    existingIds.add(entryStr);
                    addedList.add(entryStr);
                    addedCount++;
                }
            }

            if (addedCount == 0) {
                commandSource.sendSuccess(() -> Component.literal("All entries already present in tag " + tagId).withStyle(ChatFormatting.YELLOW), false);
                return 0;
            }

            try (FileWriter writer = new FileWriter(tagFilePath.toFile())) {
                GSON.toJson(tagJson, writer);
            } catch (IOException e) {
                commandSource.sendFailure(Component.literal("Failed to write to file: " + e.getMessage()));
                Constants.LOG.error("Failed to write tag file {}", tagFilePath, e);
                return 0;
            }

            Component message = Component.literal("Added " + addedCount + " entries (" + String.join(", ", addedList) + ") to tag " + tagId + " at: " + tagFilePath)
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, tagFilePath.toString())));

            commandSource.sendSuccess(() -> message, true);
            return addedCount;
        } catch (Exception e) {
            commandSource.sendFailure(Component.literal("Failed to add to tag: " + e.getMessage()));
            Constants.LOG.error("Failed to add tag", e);
            return 0;
        }
    }

    private static String getRegistryTagFolder(ResourceKey<? extends Registry<?>> registryKey) {
        ResourceLocation loc = registryKey.location();
        String path = loc.getPath();
        switch (path) {
            case "item" -> {
                return "tags/items";
            }
            case "block" -> {
                return "tags/blocks";
            }
            case "entity_type" -> {
                return "tags/entity_types";
            }
            case "fluid" -> {
                return "tags/fluids";
            }
            case "game_event" -> {
                return "tags/game_events";
            }
            case "biome" -> {
                return "tags/worldgen/biome";
            }
            case "structure" -> {
                return "tags/worldgen/structure";
            }
            case "configured_feature" -> {
                return "tags/worldgen/configured_feature";
            }
            case "placed_feature" -> {
                return "tags/worldgen/placed_feature";
            }
        }
        if (path.startsWith("worldgen/")) {
            return "tags/" + path;
        }
        return "tags/" + path + "s";
    }

    private static List<ResourceLocation> getIdsFromStack(ItemStack stack, ResourceLocation registryId) {
        List<ResourceLocation> ids = new ArrayList<>();
        if (stack.isEmpty()) {
            return ids;
        }

        if (registryId.equals(Registries.ITEM.location())) {
            ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        } else if (registryId.equals(Registries.BLOCK.location())) {
            if (stack.getItem() instanceof BlockItem blockItem) {
                ids.add(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
            }
        } else if (registryId.equals(Registries.ENTITY_TYPE.location())) {
            if (stack.getItem() instanceof SpawnEggItem spawnEgg) {
                EntityType<?> entityType = spawnEgg.getType(stack);
                ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
            }
        } else if (registryId.equals(Registries.FLUID.location())) {
            if (stack.getItem() instanceof BucketItem bucketItem) {
                try {
                    var field = BucketItem.class.getDeclaredField("content");
                    field.setAccessible(true);
                    Fluid fluid = (Fluid) field.get(bucketItem);
                    if (fluid != null) {
                        ids.add(BuiltInRegistries.FLUID.getKey(fluid));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return ids;
    }

    private enum AddSource {
        HAND, HOTBAR, INVENTORY, DIRECT
    }
}
