package com.evandev.packdev_toolkit.command;

import com.evandev.packdev_toolkit.Constants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dumps the item IDs the executing player is holding/carrying as a copyable JSON array,
 * handy for hand-writing tags, loot tables, and recipes while packdev'ing. Registered as
 * {@code /export hand|hotbar|inventory} on both loaders via the shared Brigadier dispatcher.
 */
public class ExportCommand {

    private ExportCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("export")
                .then(Commands.literal("hand")
                        .executes(ExportCommand::dumpHand))
                .then(Commands.literal("hotbar")
                        .executes(ExportCommand::dumpHotbar))
                .then(Commands.literal("inventory")
                        .executes(ExportCommand::dumpInventory))
        );
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
