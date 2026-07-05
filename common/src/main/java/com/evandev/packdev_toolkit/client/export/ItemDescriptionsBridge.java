package com.evandev.packdev_toolkit.client.export;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

public class ItemDescriptionsBridge {
    public static String getItemKey(ItemStack stack) {
        return ItemDescriptionsCompat.getItemKey(stack);
    }

    public static String getEntityKey(Entity entity) {
        return ItemDescriptionsCompat.getEntityKey(entity);
    }

    public static String getEntityTypeKey(EntityType<?> type) {
        return ItemDescriptionsCompat.getEntityTypeKey(type);
    }
}
