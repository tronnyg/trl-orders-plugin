package com.notpatch.nOrder.hook.customitem;

import com.notpatch.nOrder.Settings;
import com.notpatch.nlib.util.NLogger;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MMOItemsProvider implements CustomItemProvider {

    private final boolean available;

    public MMOItemsProvider() {
        boolean mmoFound = false;
        try {
            if (Bukkit.getPluginManager().getPlugin("MMOItems") != null) {
                if (Settings.DEBUG) {
                    NLogger.info("MMOItems plugin detected, checking API availability...");
                }
                Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
                Class.forName("net.Indyuce.mmoitems.MMOItems");
                mmoFound = true;
                if (Settings.DEBUG) {
                    NLogger.info("MMOItems API is available!");
                }
            } else {
                if (Settings.DEBUG) {
                    NLogger.info("MMOItems plugin not found in PluginManager");
                }
            }
        } catch (ClassNotFoundException e) {
            NLogger.error("MMOItems plugin found but API class not available: " + e.getMessage());
        } catch (Exception e) {
            NLogger.error("Error checking MMOItems availability: " + e.getMessage());
        }
        this.available = mmoFound;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getProviderName() {
        return "MMOItems";
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        if (!available || item == null) return false;
        try {
            NBTItem nbtItem = NBTItem.get(item);
            return nbtItem.hasTag("MMOITEMS_ITEM_ID");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        if (!available || item == null) return null;
        try {
            NBTItem nbtItem = NBTItem.get(item);
            if (!nbtItem.hasTag("MMOITEMS_ITEM_ID")) return null;

            String type = nbtItem.getString("MMOITEMS_ITEM_TYPE");
            String id = nbtItem.getString("MMOITEMS_ITEM_ID");

            return type + ":" + id;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ItemStack getCustomItem(String itemId) {
        if (!available || itemId == null) return null;
        try {
            if (Settings.DEBUG) {
                NLogger.info("MMOItems: Attempting to load item with ID: " + itemId);
            }
            String[] parts = itemId.split(":", 2);
            if (parts.length != 2) {
                if (Settings.DEBUG) {
                    NLogger.error("MMOItems: Invalid format for '" + itemId + "'. Expected format: TYPE:ITEMID");
                }
                return null;
            }

            String typeStr = parts[0];
            String id = parts[1];

            Type type = MMOItems.plugin.getTypes().get(typeStr);
            if (type == null) {
                if (Settings.DEBUG) {
                    NLogger.error("MMOItems: Type '" + typeStr + "' not found");
                }
                return null;
            }

            MMOItem mmoItem = MMOItems.plugin.getMMOItem(type, id);
            if (mmoItem == null) {
                if (Settings.DEBUG) {
                    NLogger.error("MMOItems: Item '" + id + "' of type '" + typeStr + "' not found");
                }
                return null;
            }

            ItemStack result = mmoItem.newBuilder().build();
            if (Settings.DEBUG) {
                NLogger.info("MMOItems: Successfully loaded item: " + itemId);
            }
            return result;
        } catch (Exception e) {
            NLogger.error("MMOItems: Failed to load item '" + itemId + "': " + e.getClass().getName() + " - " + e.getMessage());
            if (Settings.DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    public String getCustomItemDisplayName(ItemStack item) {
        if (!available || item == null) return null;
        try {
            NBTItem nbtItem = NBTItem.get(item);
            if (!nbtItem.hasTag("MMOITEMS_ITEM_ID")) return null;

            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return item.getItemMeta().getDisplayName();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isSameCustomItem(ItemStack item1, ItemStack item2) {
        if (!available || item1 == null || item2 == null) return false;
        try {
            String id1 = getCustomItemId(item1);
            String id2 = getCustomItemId(item2);
            if (id1 == null || !id1.equals(id2)) return false;

            return compareMMOItemStats(item1, item2);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean compareMMOItemStats(ItemStack item1, ItemStack item2) {
        try {
            NBTItem nbt1 = NBTItem.get(item1);
            NBTItem nbt2 = NBTItem.get(item2);

            Set<String> tags1 = nbt1.getTags();
            Set<String> tags2 = nbt2.getTags();

            for (String tag : tags1) {
                if (isStatTag(tag)) {
                    Object value1 = nbt1.getDouble(tag);
                    Object value2 = nbt2.getDouble(tag);

                    if (!tags2.contains(tag)) return false;
                    if (!value1.equals(value2)) return false;
                }
            }

            for (String tag : tags2) {
                if (isStatTag(tag) && !tags1.contains(tag)) {
                    return false;
                }
            }

            String gems1 = nbt1.getString("MMOITEMS_GEM_STONES");
            String gems2 = nbt2.getString("MMOITEMS_GEM_STONES");
            if (gems1 != null && !gems1.equals(gems2)) return false;
            if (gems2 != null && gems1 == null) return false;

            return true;
        } catch (Exception e) {
            if (Settings.DEBUG) {
                NLogger.error("Error comparing MMOItem stats: " + e.getMessage());
            }
            return false;
        }
    }

    private boolean isStatTag(String tag) {
        if (tag == null) return false;
        if (!tag.startsWith("MMOITEMS_")) return false;

        if (tag.equals("MMOITEMS_ITEM_ID")) return false;
        if (tag.equals("MMOITEMS_ITEM_TYPE")) return false;
        if (tag.equals("MMOITEMS_ITEM_UUID")) return false;
        if (tag.equals("MMOITEMS_REVISION_ID")) return false;

        return true;
    }

    @Override
    public List<ItemStack> getCustomItemsFromIds(List<String> itemIds) {
        List<ItemStack> items = new ArrayList<>();
        if (!available || itemIds == null) return items;

        for (String itemId : itemIds) {
            if (itemId == null || itemId.trim().isEmpty()) continue;
            try {
                ItemStack item = getCustomItem(itemId.trim());
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                NLogger.error("Failed to load MMOItems item: " + itemId + " - " + e.getMessage());
            }
        }

        NLogger.info("Loaded " + items.size() + " MMOItems from config.");
        return items;
    }
}

