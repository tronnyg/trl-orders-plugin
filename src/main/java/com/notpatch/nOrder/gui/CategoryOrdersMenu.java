package com.notpatch.nOrder.gui;

import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.model.Order;
import com.notpatch.nOrder.model.OrderCategory;
import com.notpatch.nOrder.util.ItemStackHelper;
import com.notpatch.nOrder.util.StringUtil;
import com.notpatch.nlib.effect.NSound;
import com.notpatch.nlib.fastinv.FastInv;
import com.notpatch.nlib.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CategoryOrdersMenu extends FastInv {

    private final NOrder main;
    private final OrderCategory category;
    private final Configuration config;
    private final Player player;

    private List<Order> orders;
    private int page = 0;

    private List<Integer> orderSlots = new ArrayList<>();

    public CategoryOrdersMenu(OrderCategory category, Player player) {
        super(NOrder.getInstance().getConfigurationManager().getMenuConfiguration().getConfiguration().getInt("category-order-menu.size"),
                ColorUtil.hexColor(category.getCategoryName())); // Use category name as title

        this.main = NOrder.getInstance();
        this.category = category;
        this.player = player;
        this.config = main.getConfigurationManager().getMenuConfiguration().getConfiguration();

        this.orders = main.getOrderCategoryManager().getOrdersInCategory(category.getCategoryId());

        // Load order slots from config
        List<?> slotsList = config.getList("category-order-menu.order-slots");
        if (slotsList != null) {
            orderSlots = new ArrayList<>();
            for (Object slot : slotsList) {
                if (slot instanceof Integer) {
                    orderSlots.add((Integer) slot);
                }
            }
        }

        loadMenuItems();
        updateOrderDisplay();
    }

    private void loadMenuItems() {
        ConfigurationSection itemsSection = config.getConfigurationSection("category-order-menu.items");

        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);

                if (itemSection != null) {
                    ItemStack item = ItemStackHelper.fromSection(itemSection);
                    String action = itemSection.getString("action", "");

                    if (itemSection.contains("slot")) {
                        int slot = itemSection.getInt("slot");
                        setItem(slot, item, e -> {
                            if (!action.isEmpty()) {
                                handleAction(action, e);
                            }
                        });
                    } else if (itemSection.contains("slots")) {
                        List<Integer> slots = parseSlots(itemSection.getString("slots"));

                        // Filler items
                        for (int slot : slots) {
                            setItem(slot, item);
                        }
                    }
                }
            }
        }
    }

    private void updateOrderDisplay() {
        // Clear order slots
        for (int slot : orderSlots) {
            setItem(slot, null);
        }

        if (orders.isEmpty()) {
            return;
        }

        // Sort orders
        List<Order> sortedOrders = new ArrayList<>(orders);
        // Read sort type from the menu configuration (fallback to NEWEST)
        String sortType = config.getString("category-order-menu.sort-type", "NEWEST");

        if (sortType.equalsIgnoreCase("NEWEST")) {
            sortedOrders.sort(Comparator.comparing(Order::getCreatedAt).reversed());
        } else if (sortType.equalsIgnoreCase("OLDEST")) {
            sortedOrders.sort(Comparator.comparing(Order::getCreatedAt));
        } else if (sortType.equalsIgnoreCase("PRICE_HIGH")) {
            sortedOrders.sort(Comparator.comparingDouble(Order::getPrice).reversed());
        } else if (sortType.equalsIgnoreCase("PRICE_LOW")) {
            sortedOrders.sort(Comparator.comparingDouble(Order::getPrice));
        }

        // Pagination
        int itemsPerPage = orderSlots.size();
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, sortedOrders.size());

        for (int i = start; i < end; i++) {
            Order order = sortedOrders.get(i);
            int slotIndex = i - start;

            if (slotIndex >= orderSlots.size()) break;

            int slot = orderSlots.get(slotIndex);

            // Create order item from template
            ItemStack orderItem = createOrderItem(order);

            setItem(slot, orderItem, e -> {
                // Open order details menu when clicked
                new OrderDetailsMenu(order).open(player);
                NSound.click(player);
            });
        }
    }

    private ItemStack createOrderItem(Order order) {
        ConfigurationSection templateSection = config.getConfigurationSection("category-order-menu.order-item-template");

        if (templateSection == null) {
            return new ItemStack(Material.BARRIER);
        }

        // Get material from order or use template default
        Material material = order.getItem().getType();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Apply name
            String name = templateSection.getString("name", "&f%ordered_by%'s Order");
            name = StringUtil.replaceOrderPlaceholders(name, order);
            meta.setDisplayName(ColorUtil.hexColor(name));

            // Apply lore
            List<String> lore = templateSection.getStringList("lore");
            List<String> processedLore = new ArrayList<>();
            for (String line : lore) {
                processedLore.add(ColorUtil.hexColor(StringUtil.replaceOrderPlaceholders(line, order)));
            }
            meta.setLore(processedLore);

            // Add enchantment glow if highlighted
            if (order.isHighlight()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private void handleAction(String action, org.bukkit.event.inventory.InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();

        switch (action.toLowerCase()) {
            case "back" -> {
                player.closeInventory();
                // Open main order menu
                new MainOrderMenu(player).open(player);
                NSound.click(player);
            }
            case "close" -> {
                player.closeInventory();
                NSound.click(player);
            }
            case "next-page", "next_page" -> {
                if ((page + 1) * orderSlots.size() < orders.size()) {
                    page++;
                    updateOrderDisplay();
                    NSound.click(player);
                } else {
                    NSound.error(player);
                }
            }
            case "previous-page", "previous_page" -> {
                if (page > 0) {
                    page--;
                    updateOrderDisplay();
                    NSound.click(player);
                } else {
                    NSound.error(player);
                }
            }
        }
    }

    private List<Integer> parseSlots(String slotsString) {
        List<Integer> slots = new ArrayList<>();

        if (slotsString == null) return slots;

        slotsString = slotsString.replace("[", "").replace("]", "");
        String[] parts = slotsString.split(",");

        for (String part : parts) {
            part = part.trim();

            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());

                for (int i = start; i <= end; i++) {
                    slots.add(i);
                }
            } else {
                slots.add(Integer.parseInt(part));
            }
        }
        return slots;
    }
}