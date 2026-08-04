package com.evandev.packdev_toolkit.command;

import com.evandev.packdev_toolkit.client.export.ExportPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Searches loaded datapack JSON/NBT resources for references to a
 * given item or block, and dumps the matches to a JSON file for review.
 */
public class QueryCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private QueryCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("query")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("find_item_in_loot")
                        .then(Commands.argument("item", ResourceArgument.resource(buildContext, Registries.ITEM))
                                .executes(QueryCommand::findItemInLoot)))
                .then(Commands.literal("find_block_in_features")
                        .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                .executes(QueryCommand::findBlockInFeatures)))
                .then(Commands.literal("find_block_in_structures")
                        .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                .executes(QueryCommand::findBlockInStructures)))
        );
    }

    private static int findItemInLoot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Holder.Reference<Item> itemHolder = ResourceArgument.getResource(context, "item", Registries.ITEM);
        ResourceLocation itemId = itemHolder.key().location();

        JsonArray matches = searchInJsonResources(source, itemId.toString(), "loot_table", "loot_tables", "loot_modifiers");
        return writeResult(source, "loot_containing_" + itemId.getPath() + ".json", matches);
    }

    private static int findBlockInFeatures(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Holder.Reference<Block> blockHolder = ResourceArgument.getResource(context, "block", Registries.BLOCK);
        ResourceLocation blockId = blockHolder.key().location();

        JsonArray matches = searchInJsonResources(source, blockId.toString(), "worldgen/configured_feature");
        return writeResult(source, "features_containing_" + blockId.getPath() + ".json", matches);
    }

    private static int findBlockInStructures(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Holder.Reference<Block> blockHolder = ResourceArgument.getResource(context, "block", Registries.BLOCK);
        ResourceLocation blockId = blockHolder.key().location();
        String targetBlockStr = blockId.toString();

        JsonArray matches = new JsonArray();
        ResourceManager manager = source.getServer().getResourceManager();

        Map<ResourceLocation, Resource> structures = new java.util.HashMap<>(manager.listResources("structure", path -> path.getPath().endsWith(".nbt")));
        structures.putAll(manager.listResources("structures", path -> path.getPath().endsWith(".nbt")));

        for (Map.Entry<ResourceLocation, Resource> entry : structures.entrySet()) {
            try (InputStream is = entry.getValue().open()) {
                CompoundTag nbt = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());

                if (nbt.contains("palette", Tag.TAG_LIST)) {
                    ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
                    for (int i = 0; i < palette.size(); i++) {
                        CompoundTag blockState = palette.getCompound(i);
                        if (blockState.contains("Name", Tag.TAG_STRING) && blockState.getString("Name").equals(targetBlockStr)) {
                            String path = entry.getKey().getPath();
                            if (path.startsWith("structure/")) path = path.substring(10);
                            else if (path.startsWith("structures/")) path = path.substring(11);
                            if (path.endsWith(".nbt")) path = path.substring(0, path.length() - 4);

                            matches.add(entry.getKey().getNamespace() + ":" + path);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return writeResult(source, "structures_containing_" + blockId.getPath() + ".json", matches);
    }

    private static JsonArray searchInJsonResources(CommandSourceStack source, String searchTerm, String... folders) {
        JsonArray matches = new JsonArray();
        ResourceManager manager = source.getServer().getResourceManager();
        String quotedSearch = "\"" + searchTerm + "\"";

        for (String folder : folders) {
            Map<ResourceLocation, Resource> resources = manager.listResources(folder, path -> path.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    String contentStr = reader.lines().collect(Collectors.joining("\n"));

                    if (contentStr.contains(quotedSearch)) {
                        String path = entry.getKey().getPath();
                        for (String f : folders) {
                            if (path.startsWith(f + "/")) {
                                path = path.substring(f.length() + 1);
                                break;
                            }
                        }
                        if (path.endsWith(".json")) {
                            path = path.substring(0, path.length() - 5);
                        }
                        matches.add(entry.getKey().getNamespace() + ":" + path);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return matches;
    }

    private static int writeResult(CommandSourceStack source, String filename, JsonArray matches) {
        Path exportDir = ExportPaths.queriesRoot(source.getServer().getServerDirectory());
        try {
            Files.createDirectories(exportDir);
            Path filePath = exportDir.resolve(filename);
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Files.writeString(filePath, GSON.toJson(matches));

            Component message = Component.literal("Found " + matches.size() + " matches! Exported to: " + filePath)
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, filePath.toString())));

            source.sendSuccess(() -> message, true);
            return matches.size();
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to write file: " + e.getMessage()));
            return 0;
        }
    }
}
