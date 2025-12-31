package com.notpatch.nOrder.hook;

import com.notpatch.nlib.util.NLogger;
import dev.lone.itemsadder.api.CustomStack;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemsAdderHook {

    @Getter
    private final boolean isAvailable;

    public ItemsAdderHook() {
        isAvailable = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    public boolean isCustomItem(ItemStack item) {
        if (!isAvailable || item == null) return false;
        try {
            return CustomStack.byItemStack(item) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public String getCustomItemId(ItemStack item) {
        if (!isAvailable || item == null) return null;
        try {
            CustomStack customStack = CustomStack.byItemStack(item);
            return customStack != null ? customStack.getNamespacedID() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public ItemStack getCustomItem(String namespaceId) {
        if (!isAvailable || namespaceId == null) return null;
        try {
            CustomStack customStack = CustomStack.getInstance(namespaceId);
            return customStack != null ? customStack.getItemStack() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getCustomItemDisplayName(ItemStack item) {
        if (!isAvailable || item == null) return null;
        try {
            CustomStack customStack = CustomStack.byItemStack(item);
            return customStack != null ? customStack.getDisplayName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isSameCustomItem(ItemStack item1, ItemStack item2) {
        if (!isAvailable || item1 == null || item2 == null) return false;

        try {
            CustomStack custom1 = CustomStack.byItemStack(item1);
            CustomStack custom2 = CustomStack.byItemStack(item2);

            if (custom1 == null || custom2 == null) return false;

            return custom1.getNamespacedID().equals(custom2.getNamespacedID());
        } catch (Exception e) {
            return false;
        }
    }

    public List<ItemStack> getCustomItemsFromIds(List<String> namespaceIds) {
        List<ItemStack> items = new ArrayList<>();

        if (!isAvailable) {
            return items;
        }

        if (namespaceIds == null) {
            return items;
        }


        for (String namespaceId : namespaceIds) {
            if (namespaceId == null || namespaceId.trim().isEmpty()) {
                continue;
            }

            String trimmedId = namespaceId.trim();

            try {
                ItemStack item = getCustomItem(trimmedId);
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        NLogger.info("Total " + items.size() + " valid ItemsAdder items loaded from config.");
        return items;
    }

}

