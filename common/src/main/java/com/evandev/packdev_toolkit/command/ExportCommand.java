package com.evandev.packdev_toolkit.command;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.client.export.ExportPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Dumps the item IDs the executing player is holding/carrying as a copyable JSON array.
 */
public class ExportCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ExportCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("export")
                .then(Commands.literal("hand")
                        .executes(ExportCommand::dumpHand))
                .then(Commands.literal("hotbar")
                        .executes(ExportCommand::dumpHotbar))
                .then(Commands.literal("inventory")
                        .executes(ExportCommand::dumpInventory))
                .then(Commands.literal("biomes")
                        .executes(context -> exportRegistry(context, Registries.BIOME, "biomes.json")))
                .then(Commands.literal("structures")
                        .executes(context -> exportRegistry(context, Registries.STRUCTURE, "structures.json")))
                .then(Commands.literal("configured_features")
                        .executes(context -> exportRegistry(context, Registries.CONFIGURED_FEATURE, "configured_features.json")))
                .then(Commands.literal("placed_features")
                        .executes(context -> exportRegistry(context, Registries.PLACED_FEATURE, "placed_features.json")))
                .then(Commands.literal("registry")
                        .then(Commands.argument("registry", ResourceLocationArgument.id())
                                .suggests(ExportCommand::suggestRegistries)
                                .executes(ExportCommand::exportRegistryDynamic)))
                .then(Commands.literal("tag")
                        .then(Commands.argument("registry", ResourceLocationArgument.id())
                                .suggests(ExportCommand::suggestRegistries)
                                .then(Commands.argument("tag", ResourceLocationArgument.id())
                                        .suggests(ExportCommand::suggestTags)
                                        .executes(ExportCommand::exportTag))))
                .then(Commands.literal("tags_of")
                        .then(Commands.argument("registry", ResourceLocationArgument.id())
                                .suggests(ExportCommand::suggestRegistries)
                                .then(Commands.argument("entry", ResourceLocationArgument.id())
                                        .suggests(ExportCommand::suggestRegistryEntries)
                                        .executes(ExportCommand::exportTagsOf))))
        );
    }

    private static <T> int exportRegistry(CommandContext<CommandSourceStack> context, ResourceKey<Registry<T>> registryKey, String filename) {
        CommandSourceStack source = context.getSource();
        try {
            Registry<T> registry = source.registryAccess().registryOrThrow(registryKey);
            List<String> ids = registry.keySet().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .toList();

            return writeListResult(source, filename, ids);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to export registry " + registryKey.location() + ": " + e.getMessage()));
            Constants.LOG.error("Failed to export registry {}", registryKey.location(), e);
            return 0;
        }
    }

    private static int exportRegistryDynamic(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ResourceLocation registryId = ResourceLocationArgument.getId(context, "registry");
            ResourceKey<? extends Registry<?>> registryKey = ResourceKey.createRegistryKey(registryId);
            Registry<?> registry = source.registryAccess().registry(registryKey)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown registry: " + registryId));

            List<String> ids = registry.keySet().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .toList();

            String filename = registryId.getNamespace() + "_" + registryId.getPath() + ".json";
            return writeListResult(source, filename, ids);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to export registry: " + e.getMessage()));
            return 0;
        }
    }

    private static int exportTag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ResourceLocation registryId = ResourceLocationArgument.getId(context, "registry");
            ResourceLocation tagId = ResourceLocationArgument.getId(context, "tag");

            ResourceKey<? extends Registry<Object>> registryKey = ResourceKey.createRegistryKey(registryId);
            Registry<Object> registry = source.registryAccess().registry(registryKey)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown registry: " + registryId));

            TagKey<Object> tagKey = TagKey.create(registryKey, tagId);
            Optional<HolderSet.Named<Object>> tagOpt = registry.getTag(tagKey);

            if (tagOpt.isEmpty()) {
                source.sendFailure(Component.literal("Tag not found: " + tagId + " in registry " + registryId));
                return 0;
            }

            List<String> entries = new ArrayList<>();
            for (Holder<Object> holder : tagOpt.get()) {
                holder.unwrapKey().map(ResourceKey::location).ifPresent(loc -> entries.add(loc.toString()));
            }

            entries.sort(String::compareTo);

            String filename = "tag_" + registryId.getPath() + "_" + tagId.getNamespace() + "_" + tagId.getPath() + ".json";
            return writeListResult(source, filename, entries);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to export tag: " + e.getMessage()));
            return 0;
        }
    }

    static CompletableFuture<Suggestions> suggestRegistries(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
                context.getSource().registryAccess().registries()
                        .map(entry -> entry.key().location())
                        .toList(),
                builder
        );
    }

    static CompletableFuture<Suggestions> suggestTags(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ResourceLocation registryId = ResourceLocationArgument.getId(context, "registry");
            ResourceKey<? extends Registry<Object>> registryKey = ResourceKey.createRegistryKey(registryId);
            Optional<Registry<Object>> registryOpt = context.getSource().registryAccess().registry(registryKey);

            if (registryOpt.isPresent()) {
                Registry<Object> registry = registryOpt.get();
                return SharedSuggestionProvider.suggestResource(
                        registry.getTagNames().map(TagKey::location).toList(),
                        builder
                );
            }
        } catch (Exception ignored) {
        }
        return Suggestions.empty();
    }

    static CompletableFuture<Suggestions> suggestRegistryEntries(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ResourceLocation registryId = ResourceLocationArgument.getId(context, "registry");
            ResourceKey<? extends Registry<Object>> registryKey = ResourceKey.createRegistryKey(registryId);
            Optional<Registry<Object>> registryOpt = context.getSource().registryAccess().registry(registryKey);

            if (registryOpt.isPresent()) {
                Registry<Object> registry = registryOpt.get();
                return SharedSuggestionProvider.suggestResource(
                        registry.keySet(),
                        builder
                );
            }
        } catch (Exception ignored) {
        }
        return Suggestions.empty();
    }

    private static int exportTagsOf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ResourceLocation registryId = ResourceLocationArgument.getId(context, "registry");
            ResourceLocation entryId = ResourceLocationArgument.getId(context, "entry");

            ResourceKey<? extends Registry<Object>> registryKey = ResourceKey.createRegistryKey(registryId);
            Registry<Object> registry = source.registryAccess().registry(registryKey)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown registry: " + registryId));

            ResourceKey<Object> entryKey = ResourceKey.create(registryKey, entryId);
            Optional<Holder.Reference<Object>> holderOpt = registry.getHolder(entryKey);

            if (holderOpt.isEmpty()) {
                source.sendFailure(Component.literal("Entry not found: " + entryId + " in registry " + registryId));
                return 0;
            }

            List<String> tags = holderOpt.get().tags()
                    .map(tagKey -> tagKey.location().toString())
                    .sorted()
                    .toList();

            String filename = "tags_of_" + registryId.getPath() + "_" + entryId.getNamespace() + "_" + entryId.getPath() + ".json";
            return writeListResult(source, filename, tags);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to export tags: " + e.getMessage()));
            return 0;
        }
    }

    private static int writeListResult(CommandSourceStack source, String filename, List<String> entries) {
        JsonArray array = new JsonArray();
        for (String entry : entries) {
            array.add(entry);
        }

        Path exportDir = ExportPaths.queriesRoot(source.getServer().getServerDirectory());
        try {
            Files.createDirectories(exportDir);
            Path filePath = exportDir.resolve(filename);
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Files.writeString(filePath, GSON.toJson(array));

            Component message = Component.literal("Exported " + entries.size() + " entries to: " + filePath)
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, filePath.toString())));

            source.sendSuccess(() -> message, true);
            return entries.size();
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to write file: " + e.getMessage()));
            Constants.LOG.error("Failed to write file {}", filename, e);
            return 0;
        }
    }

    private static int dumpHand(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ItemStack stack = player.getMainHandItem();

            if (stack.isEmpty()) {
                context.getSource().sendFailure(Component.translatable("command.packdev_toolkit.dump.hand_empty"));
                return 0;
            }

            sendCopyableMessage(context.getSource(), List.of(getItemId(stack)), "command.packdev_toolkit.dump.title.hand");
            return 1;
        } catch (Exception e) {
            Constants.LOG.error("Failed to dump hand item", e);
            return 0;
        }
    }

    private static int dumpHotbar(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Inventory inventory = player.getInventory();
            List<String> items = new ArrayList<>();

            for (int i = 0; i < 9; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    items.add(getItemId(stack));
                }
            }

            ItemStack offhand = inventory.offhand.getFirst();
            if (!offhand.isEmpty()) {
                items.add(getItemId(offhand));
            }

            if (items.isEmpty()) {
                context.getSource().sendFailure(Component.translatable("command.packdev_toolkit.dump.hotbar_empty"));
                return 0;
            }

            List<String> uniqueItems = items.stream().distinct().toList();
            sendCopyableMessage(context.getSource(), uniqueItems, "command.packdev_toolkit.dump.title.hotbar");
            return uniqueItems.size();
        } catch (Exception e) {
            Constants.LOG.error("Failed to dump hotbar items", e);
            return 0;
        }
    }

    private static int dumpInventory(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Inventory inventory = player.getInventory();
            List<String> items = new ArrayList<>();

            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    items.add(getItemId(stack));
                }
            }

            if (items.isEmpty()) {
                context.getSource().sendFailure(Component.translatable("command.packdev_toolkit.dump.inventory_empty"));
                return 0;
            }

            List<String> uniqueItems = items.stream().distinct().toList();
            sendCopyableMessage(context.getSource(), uniqueItems, "command.packdev_toolkit.dump.title.inventory");
            return uniqueItems.size();
        } catch (Exception e) {
            Constants.LOG.error("Failed to dump inventory items", e);
            return 0;
        }
    }

    private static String getItemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void sendCopyableMessage(CommandSourceStack source, List<String> itemIds, String titleKey) {
        String jsonArray = itemIds.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(", ", "[", "]"));

        Component message = Component.literal(jsonArray)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, jsonArray))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("command.packdev_toolkit.dump.copy_tooltip")))
                );

        source.sendSuccess(() -> Component.translatable(titleKey).withStyle(ChatFormatting.GOLD).append(":"), false);
        source.sendSuccess(() -> message, false);
    }
}
