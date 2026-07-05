package com.evandev.packdev_toolkit.client.export;

import cc.cassian.item_descriptions.client.descriptions.EntityDescriptions;
import cc.cassian.item_descriptions.client.descriptions.ItemDescriptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

public class ItemDescriptionsCompat {
    public static String getItemKey(ItemStack stack) {
        return ItemDescriptions.findLoreKey(stack).toString();
    }

    public static String getEntityKey(Entity entity) {
        return EntityDescriptions.findLoreKey(entity).toString();
    }

    public static String getEntityTypeKey(EntityType<?> type) {
        return EntityDescriptions.findLoreKey(type).toString();
    }
}
