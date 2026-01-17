package com.notpatch.nOrder.gui;

import com.notpatch.nOrder.LanguageLoader;
import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.Settings;
import com.notpatch.nOrder.model.AdminOrder;
import com.notpatch.nOrder.model.OrderStatus;
import com.notpatch.nOrder.util.ItemStackHelper;
import com.notpatch.nlib.effect.NSound;
import com.notpatch.nlib.fastinv.FastInv;
import com.notpatch.nlib.util.ColorUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NewAdminOrderMenu extends FastInv implements Listener {

    private final NOrder main;

    @Getter
    private ItemStack selectedItem;

    @Getter
    @Setter
    private int quantity = 1;

    @Getter
    @Setter
    private double pricePerItem = 1.0;

    @Getter
    @Setter
    private boolean isHighlighted = false;

    @Getter
    @Setter
    private String customName = "";

    @Getter
    @Setter
    private String categoryId = null;

    @Getter
    @Setter
    private long cooldownDuration = 0; // in seconds

    @Getter
    @Setter
    private boolean repeatable = false;

    @Getter
    @Setter
    private int durationDays = 7; // Default expiration duration

    public NewAdminOrderMenu() {
        super(54, ColorUtil.hexColor("&6&lNew Admin Order"));
        this.main = NOrder.getInstance();
        initializeMenu();
    }

    private void initializeMenu() {
        // Background
        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = background.getItemMeta();
        if (backgroundMeta != null) {
            backgroundMeta.setDisplayName(" ");
            background.setItemMeta(backgroundMeta);
        }
        for (int i = 0; i < 54; i++) {
            setItem(i, background);
        }

        updateMenuItems();
    }

    public void setSelectedItem(ItemStack item) {
        this.selectedItem = item;
        updateMenuItems();
    }

    public void setSelectedItem(Material material) {
        this.selectedItem = new ItemStack(material);
        updateMenuItems();
    }

    public void updateMenuItems() {
        // Select Item (Slot 10)
        if (selectedItem != null) {
            ItemStack displayItem = selectedItem.clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.hexColor("&e&lSelected Item: &f" + selectedItem.getType().name()));
                List<String> lore = new ArrayList<>();
                lore.add(ColorUtil.hexColor("&7Click to change item"));
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }
            setItem(10, displayItem, e -> new ItemSelectMenu(this).open((Player) e.getWhoClicked()));
        } else {
            ItemStack selectItem = ItemStackHelper.builder()
                    .material(Material.BARRIER)
                    .displayName(ColorUtil.hexColor("&e&lSelect Item"))
                    .lore(Arrays.asList(
                            ColorUtil.hexColor("&7Click to select an item"),
                            ColorUtil.hexColor("&7for this admin order")
                    ))
                    .build();
            setItem(10, selectItem, e -> new ItemSelectMenu(this).open((Player) e.getWhoClicked()));
        }

        // Quantity (Slot 12)
        ItemStack quantityItem = ItemStackHelper.builder()
                .material(Material.PAPER)
                .displayName(ColorUtil.hexColor("&e&lQuantity: &f" + quantity))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Current: &f" + quantity),
                        "",
                        ColorUtil.hexColor("&aClick to set quantity")
                ))
                .build();
        setItem(12, quantityItem, this::handleSetQuantity);

        // Price Per Item (Slot 14)
        ItemStack priceItem = ItemStackHelper.builder()
                .material(Material.GOLD_INGOT)
                .displayName(ColorUtil.hexColor("&e&lPrice Per Item: &f$" + pricePerItem))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Current: &f$" + pricePerItem),
                        ColorUtil.hexColor("&7Total: &f$" + (quantity * pricePerItem)),
                        "",
                        ColorUtil.hexColor("&aClick to set price")
                ))
                .build();
        setItem(14, priceItem, this::handleSetPrice);

        // Custom Name (Slot 16)
        ItemStack customNameItem = ItemStackHelper.builder()
                .material(Material.NAME_TAG)
                .displayName(ColorUtil.hexColor("&e&lCustom Name"))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Current: &f" + (customName.isEmpty() ? "None" : customName)),
                        "",
                        ColorUtil.hexColor("&aClick to set custom name")
                ))
                .build();
        setItem(16, customNameItem, this::handleSetCustomName);

        // Duration (Slot 28)
        ItemStack durationItem = ItemStackHelper.builder()
                .material(Material.CLOCK)
                .displayName(ColorUtil.hexColor("&e&lExpiration Duration: &f" + durationDays + " days"))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Current: &f" + durationDays + " days"),
                        "",
                        ColorUtil.hexColor("&aClick to set duration")
                ))
                .build();
        setItem(28, durationItem, this::handleSetDuration);

        // Cooldown Duration (Slot 30)
        ItemStack cooldownItem = ItemStackHelper.builder()
                .material(Material.HOPPER)
                .displayName(ColorUtil.hexColor("&e&lCooldown: &f" + formatDuration(cooldownDuration)))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Current: &f" + formatDuration(cooldownDuration)),
                        ColorUtil.hexColor("&7Time before order can repeat"),
                        "",
                        ColorUtil.hexColor("&aClick to set cooldown")
                ))
                .build();
        setItem(30, cooldownItem, this::handleSetCooldown);

        // Repeatable Toggle (Slot 32)
        ItemStack repeatableItem = ItemStackHelper.builder()
                .material(repeatable ? Material.LIME_DYE : Material.GRAY_DYE)
                .displayName(ColorUtil.hexColor(repeatable ? "&a&lRepeatable: Enabled" : "&7&lRepeatable: Disabled"))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Status: " + (repeatable ? "&aEnabled" : "&cDisabled")),
                        "",
                        ColorUtil.hexColor("&7If enabled, order will reset"),
                        ColorUtil.hexColor("&7after cooldown period"),
                        "",
                        ColorUtil.hexColor("&eClick to toggle")
                ))
                .build();
        setItem(32, repeatableItem, e -> {
            repeatable = !repeatable;
            NSound.click((Player) e.getWhoClicked());
            updateMenuItems();
        });

        // Category (Slot 34)
        ItemStack categoryItem = ItemStackHelper.builder()
                .material(Material.BOOKSHELF)
                .displayName(ColorUtil.hexColor("&e&lCategory"))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Current: &f" + (categoryId == null ? "None" : categoryId)),
                        "",
                        ColorUtil.hexColor("&aClick to select category")
                ))
                .build();
        setItem(34, categoryItem, this::handleSelectCategory);

        // Highlight Toggle (Slot 48)
        ItemStack highlightItem = ItemStackHelper.builder()
                .material(isHighlighted ? Material.GLOWSTONE_DUST : Material.GUNPOWDER)
                .displayName(ColorUtil.hexColor(isHighlighted ? "&e&lHighlight: Enabled" : "&7&lHighlight: Disabled"))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Status: " + (isHighlighted ? "&aEnabled" : "&cDisabled")),
                        "",
                        ColorUtil.hexColor("&eClick to toggle")
                ))
                .build();
        setItem(48, highlightItem, e -> {
            isHighlighted = !isHighlighted;
            NSound.click((Player) e.getWhoClicked());
            updateMenuItems();
        });

        // Cancel (Slot 49)
        ItemStack cancelItem = ItemStackHelper.builder()
                .material(Material.RED_STAINED_GLASS_PANE)
                .displayName(ColorUtil.hexColor("&c&lCancel"))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Close without creating")
                ))
                .build();
        setItem(49, cancelItem, e -> {
            e.getWhoClicked().closeInventory();
            NSound.error((Player) e.getWhoClicked());
        });

        // Confirm (Slot 50)
        ItemStack confirmItem = ItemStackHelper.builder()
                .material(Material.LIME_STAINED_GLASS_PANE)
                .displayName(ColorUtil.hexColor("&a&lConfirm"))
                .lore(Arrays.asList(
                        ColorUtil.hexColor("&7Create this admin order")
                ))
                .build();
        setItem(50, confirmItem, this::handleConfirm);
    }

    private String formatDuration(long seconds) {
        if (seconds == 0) return "None";

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return seconds + "s";
        }
    }

    private void handleSetQuantity(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        player.closeInventory();
        player.sendMessage(ColorUtil.hexColor("&eEnter the quantity (e.g., 64):"));

        main.getChatInputManager().setAwaitingInput(player, value -> {
            processQuantityInput(player, value);
        });
    }

    private void handleSetPrice(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        player.closeInventory();
        player.sendMessage(ColorUtil.hexColor("&eEnter the price per item (e.g., 10.5):"));

        main.getChatInputManager().setAwaitingInput(player, value -> {
            processPriceInput(player, value);
        });
    }

    private void handleSetCustomName(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        player.closeInventory();
        player.sendMessage(ColorUtil.hexColor("&eEnter custom name (or 'none' to clear):"));

        main.getChatInputManager().setAwaitingInput(player, value -> {
            processCustomNameInput(player, value);
        });
    }

    private void handleSetDuration(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        player.closeInventory();
        player.sendMessage(ColorUtil.hexColor("&eEnter expiration duration in days (e.g., 7):"));

        main.getChatInputManager().setAwaitingInput(player, value -> {
            processDurationInput(player, value);
        });
    }

    private void handleSetCooldown(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        player.closeInventory();
        player.sendMessage(ColorUtil.hexColor("&eEnter cooldown in seconds (e.g., 3600 for 1 hour):"));
        player.sendMessage(ColorUtil.hexColor("&7Examples: 3600=1h, 86400=1d, 604800=1w"));

        main.getChatInputManager().setAwaitingInput(player, value -> {
            processCooldownInput(player, value);
        });
    }

    private void handleSelectCategory(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        // TODO: Implement category selection menu
        player.closeInventory();
        player.sendMessage(ColorUtil.hexColor("&eEnter category ID (or 'none' to clear):"));

        main.getChatInputManager().setAwaitingInput(player, value -> {
            processCategoryInput(player, value);
        });
    }

    private void handleConfirm(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();

        if (selectedItem == null) {
            player.sendMessage(ColorUtil.hexColor("&cYou must select an item!"));
            NSound.error(player);
            return;
        }

        if (quantity <= 0) {
            player.sendMessage(ColorUtil.hexColor("&cQuantity must be greater than 0!"));
            NSound.error(player);
            return;
        }

        if (pricePerItem <= 0) {
            player.sendMessage(ColorUtil.hexColor("&cPrice must be greater than 0!"));
            NSound.error(player);
            return;
        }

        // Create admin order
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(durationDays);
        String id = main.getAdminOrderManager().createRandomId();

        String customItemId = null;
        if (main.getCustomItemManager() != null && main.getCustomItemManager().hasAnyProvider()) {
            customItemId = main.getCustomItemManager().getCustomItemId(selectedItem);
        }

        AdminOrder order = new AdminOrder(
                id,
                selectedItem,
                customItemId,
                quantity,
                pricePerItem,
                0, // delivered
                0, // collected
                now,
                expiresAt,
                isHighlighted,
                OrderStatus.ACTIVE,
                categoryId,
                customName.isEmpty() ? null : customName,
                cooldownDuration,
                repeatable,
                null, // cooldownEndsAt
                null  // lastCompletedAt
        );

        main.getAdminOrderManager().addAdminOrder(order);

        player.sendMessage(ColorUtil.hexColor("&a&lAdmin order created successfully!"));
        player.sendMessage(ColorUtil.hexColor("&7Order ID: &e" + id));
        player.sendMessage(ColorUtil.hexColor("&7Item: &f" + selectedItem.getType().name()));
        player.sendMessage(ColorUtil.hexColor("&7Quantity: &f" + quantity));
        player.sendMessage(ColorUtil.hexColor("&7Price: &f$" + pricePerItem + " each"));
        player.sendMessage(ColorUtil.hexColor("&7Total: &f$" + (quantity * pricePerItem)));
        if (repeatable) {
            player.sendMessage(ColorUtil.hexColor("&7Repeatable: &aYes &7(Cooldown: " + formatDuration(cooldownDuration) + ")"));
        }

        NSound.success(player);
        player.closeInventory();

        // Log action
        main.getOrderLogger().logAdminAction(player.getName(), "CREATE_ADMIN_ORDER",
                "Created admin order " + id + " for " + quantity + "x " + selectedItem.getType().name());
    }

    private void processQuantityInput(Player player, String input) {
        try {
            int value = Integer.parseInt(input);
            if (value <= 0) {
                player.sendMessage(ColorUtil.hexColor("&cQuantity must be greater than 0!"));
                NSound.error(player);
                return;
            }
            quantity = value;
            NSound.success(player);
            main.getMorePaperLib().scheduling().globalRegionalScheduler().run(() -> {
                updateMenuItems();
                this.open(player);
            });
        } catch (NumberFormatException ex) {
            player.sendMessage(ColorUtil.hexColor("&cInvalid number! Please enter a valid quantity."));
            NSound.error(player);
        }
    }

    private void processPriceInput(Player player, String input) {
        try {
            double value = Double.parseDouble(input);
            if (value <= 0) {
                player.sendMessage(ColorUtil.hexColor("&cPrice must be greater than 0!"));
                NSound.error(player);
                return;
            }
            pricePerItem = value;
            NSound.success(player);
            main.getMorePaperLib().scheduling().globalRegionalScheduler().run(() -> {
                updateMenuItems();
                this.open(player);
            });
        } catch (NumberFormatException ex) {
            player.sendMessage(ColorUtil.hexColor("&cInvalid number! Please enter a valid price."));
            NSound.error(player);
        }
    }

    private void processCustomNameInput(Player player, String input) {
        if (input.equalsIgnoreCase("none") || input.equalsIgnoreCase("clear")) {
            customName = "";
        } else {
            customName = input;
        }
        NSound.success(player);
        main.getMorePaperLib().scheduling().globalRegionalScheduler().run(() -> {
            updateMenuItems();
            this.open(player);
        });
    }

    private void processDurationInput(Player player, String input) {
        try {
            int value = Integer.parseInt(input);
            if (value <= 0) {
                player.sendMessage(ColorUtil.hexColor("&cDuration must be greater than 0!"));
                NSound.error(player);
                return;
            }
            durationDays = value;
            NSound.success(player);
            main.getMorePaperLib().scheduling().globalRegionalScheduler().run(() -> {
                updateMenuItems();
                this.open(player);
            });
        } catch (NumberFormatException ex) {
            player.sendMessage(ColorUtil.hexColor("&cInvalid number! Please enter a valid duration in days."));
            NSound.error(player);
        }
    }

    private void processCooldownInput(Player player, String input) {
        try {
            long value = Long.parseLong(input);
            if (value < 0) {
                player.sendMessage(ColorUtil.hexColor("&cCooldown cannot be negative!"));
                NSound.error(player);
                return;
            }
            cooldownDuration = value;
            NSound.success(player);
            main.getMorePaperLib().scheduling().globalRegionalScheduler().run(() -> {
                updateMenuItems();
                this.open(player);
            });
        } catch (NumberFormatException ex) {
            player.sendMessage(ColorUtil.hexColor("&cInvalid number! Please enter a valid cooldown in seconds."));
            NSound.error(player);
        }
    }

    private void processCategoryInput(Player player, String input) {
        if (input.equalsIgnoreCase("none") || input.equalsIgnoreCase("clear")) {
            categoryId = null;
        } else {
            categoryId = input;
        }
        NSound.success(player);
        main.getMorePaperLib().scheduling().globalRegionalScheduler().run(() -> {
            updateMenuItems();
            this.open(player);
        });
    }

    @Override
    protected void onClose(InventoryCloseEvent event) {
        super.onClose(event);
        Player player = (Player) event.getPlayer();
        main.getChatInputManager().cancelInput(player);
    }

    @Override
    protected void onClick(InventoryClickEvent event) {
        if (event.getCursor().getType() != Material.AIR) {
            NSound.click((Player) event.getWhoClicked());
        }
    }
}

