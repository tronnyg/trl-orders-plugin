package com.notpatch.nOrder.command;

import com.notpatch.nOrder.LanguageLoader;
import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.Settings;
import com.notpatch.nOrder.model.Order;
import com.notpatch.nOrder.model.OrderCategory;
import com.notpatch.nOrder.model.OrderStatus;
import com.notpatch.nOrder.util.NumberFormatter;
import com.notpatch.nOrder.util.StringUtil;
import com.notpatch.nlib.effect.NSound;
import com.notpatch.nlib.util.ColorUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OrderAdminCommand implements BasicCommand {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {

        Entity entity = commandSourceStack.getExecutor();
        CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                if (!(entity instanceof Player player)) {
                    sender.sendMessage(ColorUtil.hexColor("&cThis command can only be used by players."));
                    return;
                }
                NOrder.getInstance().getMorePaperLib().scheduling().globalRegionalScheduler().run(() -> {
                    new com.notpatch.nOrder.gui.NewAdminOrderMenu().open(player);
                });
                NSound.click(player);
            }
            case "reload" -> {
                if (args.length == 1) {
                    reloadConfigurations(sender);
                    if (entity instanceof Player player) {
                        NSound.success(player);
                    }
                }
            }
            case "info" -> {
                if (args.length == 2) {
                    handleInfoCommand(sender, entity, args[1]);
                } else {
                    sender.sendMessage(LanguageLoader.getMessage("admin-usage-info"));
                }
            }
            case "delete" -> {
                if (args.length == 2) {
                    handleDeleteCommand(sender, entity, args[1]);
                } else {
                    sender.sendMessage(LanguageLoader.getMessage("admin-usage-delete"));
                }
            }
            case "player" -> {
                if (args.length >= 2) {
                    int limit = args.length >= 3 ? parseIntOrDefault(args[2], 10) : 10;
                    handlePlayerCommand(sender, entity, args[1], limit);
                } else {
                    sender.sendMessage(LanguageLoader.getMessage("admin-usage-player"));
                }
            }
            case "category" -> {
                if (args.length >= 2) {
                    handleCategoryCommand(sender, entity, Arrays.copyOfRange(args, 1, args. length));
                } else {
                    sender.sendMessage(LanguageLoader.getMessage("admin-usage-category"));
                }
            }
            default -> sendUsage(sender);
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorUtil.hexColor("&e&l/orderadmin Commands:"));
        sender.sendMessage(ColorUtil.hexColor("&7/orderadmin &fcreate &7- Open admin order creation menu"));
        sender.sendMessage(LanguageLoader.getMessage("admin-usage-reload"));
        sender.sendMessage(LanguageLoader.getMessage("admin-usage-info"));
        sender.sendMessage(LanguageLoader.getMessage("admin-usage-delete"));
        sender.sendMessage(LanguageLoader.getMessage("admin-usage-player"));
        sender.sendMessage(LanguageLoader.getMessage("admin-usage-category"));
    }

    private void handleCategoryCommand(CommandSender sender, Entity entity, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtil.hexColor("&e&l/orderadmin category Usage:"));
            sender.sendMessage(ColorUtil.hexColor("&7/orderadmin category &fcreate &7- Create a new category (interactive)"));
            sender.sendMessage(ColorUtil.hexColor("&7/orderadmin category &fedit <id> &7- Edit category name (interactive)"));
            sender.sendMessage(ColorUtil.hexColor("&7/orderadmin category &fdelete <name> &7- Delete a category"));
            sender.sendMessage(ColorUtil.hexColor("&7/orderadmin category &flist &7- List all categories"));
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                if (!(entity instanceof Player player)) {
                    sender.sendMessage(ColorUtil.hexColor("&cThis command can only be used by players."));
                    return;
                }

                player.sendMessage(ColorUtil.hexColor("&e&lCategory Creation"));
                player.sendMessage(ColorUtil.hexColor("&7Please enter the &fcategory ID &7(used for /ordercategory command):"));
                player.sendMessage(ColorUtil.hexColor("&7Example: &fcrops, materials, tools"));
                player.sendMessage(ColorUtil.hexColor("&8(Type 'cancel' to abort)"));

                NOrder.getInstance().getChatInputManager().setAwaitingInput(player, categoryId -> {
                    if (categoryId.equalsIgnoreCase("cancel")) {
                        player.sendMessage(ColorUtil.hexColor("&cCategory creation cancelled."));
                        NSound.error(player);
                        return;
                    }

                    // Validate ID format (alphanumeric, dashes, underscores only)
                    if (!categoryId.matches("^[a-zA-Z0-9_-]+$")) {
                        player.sendMessage(ColorUtil.hexColor("&cInvalid ID! Use only letters, numbers, dashes, and underscores."));
                        NSound.error(player);
                        return;
                    }

                    // Check if ID already exists
                    if (NOrder.getInstance().getOrderCategoryManager().getCategoryById(categoryId) != null) {
                        player.sendMessage(ColorUtil.hexColor("&cA category with ID &f" + categoryId + " &calready exists!"));
                        NSound.error(player);
                        return;
                    }

                    player.sendMessage(ColorUtil.hexColor("&aID set to: &f" + categoryId));
                    player.sendMessage(ColorUtil.hexColor("&7Now enter the &fdisplay name &7(supports color codes):"));
                    player.sendMessage(ColorUtil.hexColor("&7Example: &a&lCrops &7or &6Materials"));
                    player.sendMessage(ColorUtil.hexColor("&8(Type 'cancel' to abort)"));

                    NOrder.getInstance().getChatInputManager().setAwaitingInput(player, categoryName -> {
                        if (categoryName.equalsIgnoreCase("cancel")) {
                            player.sendMessage(ColorUtil.hexColor("&cCategory creation cancelled."));
                            NSound.error(player);
                            return;
                        }

                        // Create the category with the custom ID
                        String displayItem = "CHEST"; // Default display item
                        OrderCategory category = NOrder.getInstance().getOrderCategoryManager()
                                .createCategory(categoryId, categoryName, displayItem);

                        if (category == null) {
                            player.sendMessage(ColorUtil.hexColor("&cFailed to create category. ID might already exist."));
                            NSound.error(player);
                            return;
                        }

                        player.sendMessage(ColorUtil.hexColor("&a&lCategory Created!"));
                        player.sendMessage(ColorUtil.hexColor("&7ID: &f" + categoryId));
                        player.sendMessage(ColorUtil.hexColor("&7Name: " + categoryName));
                        player.sendMessage(ColorUtil.hexColor("&7Command: &f/ordercategory " + categoryId));
                        NSound.success(player);
                    });
                });
            }
            case "edit" -> {
                if (!(entity instanceof Player player)) {
                    sender.sendMessage(ColorUtil.hexColor("&cThis command can only be used by players."));
                    return;
                }

                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.hexColor("&cUsage: /orderadmin category edit <id>"));
                    return;
                }

                String categoryId = args[1];
                OrderCategory existingCategory = NOrder.getInstance().getOrderCategoryManager()
                        .getCategoryById(categoryId);

                if (existingCategory == null) {
                    player.sendMessage(ColorUtil.hexColor("&cCategory not found: " + categoryId));
                    NSound.error(player);
                    return;
                }

                player.sendMessage(ColorUtil.hexColor("&e&lEditing Category: &f" + categoryId));
                player.sendMessage(ColorUtil.hexColor("&7Current Name: " + existingCategory.getCategoryName()));
                player.sendMessage(ColorUtil.hexColor(""));
                player.sendMessage(ColorUtil.hexColor("&7Please enter the &fnew display name &7(supports color codes):"));
                player.sendMessage(ColorUtil.hexColor("&7Example: &a&lCrops &7or &6Materials"));
                player.sendMessage(ColorUtil.hexColor("&8(Type 'cancel' to abort)"));

                NOrder.getInstance().getChatInputManager().setAwaitingInput(player, newCategoryName -> {
                    if (newCategoryName.equalsIgnoreCase("cancel")) {
                        player.sendMessage(ColorUtil.hexColor("&cCategory editing cancelled."));
                        NSound.error(player);
                        return;
                    }

                    if (newCategoryName.trim().isEmpty()) {
                        player.sendMessage(ColorUtil.hexColor("&cCategory name cannot be empty!"));
                        NSound.error(player);
                        return;
                    }

                    // Update the category name only
                    boolean success = NOrder.getInstance().getOrderCategoryManager()
                            .updateCategory(categoryId, newCategoryName);

                    if (success) {
                        player.sendMessage(ColorUtil.hexColor("&a&lCategory Updated!"));
                        player.sendMessage(ColorUtil.hexColor("&7ID: &f" + categoryId));
                        player.sendMessage(ColorUtil.hexColor("&7Old Name: " + existingCategory.getCategoryName()));
                        player.sendMessage(ColorUtil.hexColor("&7New Name: " + newCategoryName));
                        NSound.success(player);
                    } else {
                        player.sendMessage(ColorUtil.hexColor("&cFailed to update category."));
                        NSound.error(player);
                    }
                });
            }
            case "delete" -> {
                if (args.length >= 2) {
                    String categoryID = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    OrderCategory category = NOrder.getInstance().getOrderCategoryManager()
                            .getCategoryById(categoryID);

                    if (category == null) {
                        sender.sendMessage(ColorUtil.hexColor("&cCategory not found: " + categoryID));
                        if (entity instanceof Player player) {
                            NSound.error(player);
                        }
                        return;
                    }

                    boolean success = NOrder.getInstance().getOrderCategoryManager()
                            .deleteCategory(category.getCategoryId());

                    if (success) {
                        sender.sendMessage(ColorUtil.hexColor("&aCategory deleted: " + categoryID));
                        if (entity instanceof Player player) {
                            NSound.success(player);
                        }
                    } else {
                        sender.sendMessage(ColorUtil.hexColor("&cFailed to delete category."));
                        if (entity instanceof Player player) {
                            NSound.error(player);
                        }
                    }
                } else {
                    sender.sendMessage(ColorUtil.hexColor("&cUsage: /orderadmin category delete <name>"));
                }
            }
            case "list" -> {
                Collection<OrderCategory> categories = NOrder.getInstance().getOrderCategoryManager()
                        .getAllCategories();

                if (categories.isEmpty()) {
                    sender.sendMessage(ColorUtil.hexColor("&eNo categories found."));
                    return;
                }

                sender.sendMessage(ColorUtil.hexColor("&6═══════ Categories ═══════"));
                for (OrderCategory category : categories) {
                    int orderCount = NOrder.getInstance().getOrderCategoryManager().getAdminOrdersInCategory(category.getCategoryId()).size();
                    sender.sendMessage(ColorUtil.hexColor("&e" + category.getCategoryName() +
                            " &7(" + orderCount + " orders) &8[" + category.getCategoryId() + "]"));
                }
            }
            default -> {
                sender.sendMessage(ColorUtil.hexColor("&cUsage: /orderadmin category <create|delete|list>"));
            }
        }
    }

    private void handleInfoCommand(CommandSender sender, Entity entity, String orderId) {
        Order order = NOrder.getInstance().getOrderManager().getOrderById(orderId);
        if (order != null) {
            if (entity instanceof Player player) {
                NSound.success(player);
            }

            List<String> infoLore = LanguageLoader.getMessageList(("admin-info-lore"));

            for (String line : infoLore) {
                String formatted = line
                        .replace("%id%", order.getId())
                        .replace("%owner%", order.getPlayerName())
                        .replace("%item%", StringUtil.formatMaterialName(order.getMaterial()))
                        .replace("%amount%", String.valueOf(order.getAmount()))
                        .replace("%price%", NumberFormatter.format(order.getPrice()))
                        .replace("%total_price%", NumberFormatter.format(order.getPrice() * order.getAmount()))
                        .replace("%status%", order.getStatus().name())
                        .replace("%delivered%", String.valueOf(order.getDelivered()))
                        .replace("%collected%", String.valueOf(order.getCollected()))
                        .replace("%highlight%", order.isHighlight() ? LanguageLoader.getMessage("highlighted-yes") : LanguageLoader.getMessage("highlighted-no"))
                        .replace("%created%", order.getCreatedAt().format(formatter))
                        .replace("%expires%", order.getExpirationDate().format(formatter));

                sender.sendMessage(ColorUtil.hexColor(formatted));
            }

            Component actions = Component.text("[Delete]", NamedTextColor.RED)
                    .hoverEvent(HoverEvent.showText(Component.text("Click to delete order")))
                    .clickEvent(ClickEvent.suggestCommand("/orderadmin delete " + orderId));
            sender.sendMessage(actions);
        } else {
            sender.sendMessage(LanguageLoader.getMessage("order-not-found").replace("%id%", orderId));
            if (entity instanceof Player player) {
                NSound.error(player);
            }
        }
    }

    private void handleDeleteCommand(CommandSender sender, Entity entity, String orderId) {
        Order order = NOrder.getInstance().getOrderManager().getOrderById(orderId);
        if (order != null) {
            String adminName = entity instanceof Player player ? player.getName() : "Console";
            NOrder.getInstance().getOrderLogger().logAdminAction(adminName, "DELETE", order);
            NOrder.getInstance().getOrderManager().removeOrder(order);
            sender.sendMessage(LanguageLoader.getMessage("order-deleted").replace("%id%", orderId));
            if (entity instanceof Player player) {
                NSound.success(player);
            }
        } else {
            sender.sendMessage(LanguageLoader.getMessage("order-not-found").replace("%id%", orderId));
            if (entity instanceof Player player) {
                NSound.error(player);
            }
        }
    }


    private void handlePlayerCommand(CommandSender sender, Entity entity, String playerName, int limit) {
        List<Order> playerOrders = NOrder.getInstance().getOrderManager().getPlayerOrdersIncludingCompleted(playerName);

        if (playerOrders.isEmpty()) {
            sender.sendMessage(LanguageLoader.getMessage("player-no-orders").replace("%player%", playerName));
            if (entity instanceof Player player) {
                NSound.error(player);
            }
            return;
        }

        if (entity instanceof Player player) {
            NSound.success(player);
        }
    }

    private NamedTextColor getStatusColor(OrderStatus status) {
        return switch (status) {
            case ACTIVE -> NamedTextColor.GREEN;
            case COMPLETED -> NamedTextColor.BLUE;
            case CANCELLED -> NamedTextColor.RED;
            case ARCHIVED -> NamedTextColor.GRAY;
            case COOLDOWN -> NamedTextColor.YELLOW;
        };
    }


    private int parseIntOrDefault(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {

        List<String> suggestions = List.of("create", "reload", "info", "delete", "player", "category");

        if (args.length == 0) {
            return suggestions;
        } else if (args.length == 1) {
            String input = args[0].toLowerCase();
            return suggestions.stream()
                    .filter(suggestion -> suggestion.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            return switch (subCommand) {
                case "info", "delete" -> NOrder.getInstance().getOrderManager().getAllOrders().stream()
                        .map(Order::getId)
                        .filter(id -> id.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
                case "player" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
                case "category" -> List.of("create", "edit", "delete", "list");
                default -> Collections.emptyList();
            };
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("player")) {
                return List.of("10", "20", "50", "100");
            } else if (subCommand.equals("category")) {
                String categorySubCommand = args[1].toLowerCase();
                String input = args[2].toLowerCase();

                return switch (categorySubCommand) {
                    case "edit", "delete" ->
                        NOrder.getInstance().getOrderCategoryManager().getAllCategories().stream()
                            .map(OrderCategory::getCategoryId)
                            .filter(id -> id.toLowerCase().startsWith(input))
                            .collect(Collectors.toList());
                    default -> Collections.emptyList();
                };
            }
        } else if (args.length == 4) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("category")) {
                String categorySubCommand = args[1].toLowerCase();
                String input = args[3].toLowerCase();
            }
        }
        return Collections.emptyList();
    }
    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(Settings.ORDER_ADMIN_PERMISSION);
    }

    @Override
    public @Nullable String permission() {
        return Settings.ORDER_ADMIN_PERMISSION;
    }

    private void reloadConfigurations(CommandSender sender) {
        NOrder main = NOrder.getInstance();
        main.reloadConfig();
        main.saveDefaultConfig();
        main.saveConfig();

        main.getLanguageLoader().loadLangs();
        Settings.loadSettings();
        Settings.loadCustomItems();
        main.getConfigurationManager().reloadConfigurations();
        main.getWebhookManager().loadWebhooks();
        sender.sendMessage("§aConfigurations reloaded.");
    }

}
