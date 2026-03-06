package com.notpatch.nOrder.command;

import com.notpatch.nOrder.LanguageLoader;
import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.gui.NewContractMenu;
import com.notpatch.nOrder.model.Contract;
import com.notpatch.nOrder.model.ContractCategory;
import com.notpatch.nlib.effect.NSound;
import com.notpatch.nlib.util.ColorUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class ContractsCommand implements BasicCommand {

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        Entity executor = commandSourceStack.getExecutor();
        CommandSender sender = commandSourceStack.getSender();

        if (!(executor instanceof Player player)) {
            commandSourceStack. getSender().sendMessage("This command can only be used by players.");
            return;
        }

        if (args.length == 0) {
            player.sendMessage(LanguageLoader.getMessage("contract-usage"));
            NSound.error(player);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                NOrder.getInstance().getMorePaperLib().scheduling().globalRegionalScheduler().run(() -> {
                    new NewContractMenu().open(player);
                });
                NSound.click(player);
            }
            case "edit" -> {
                NSound.click(player);
            }
            case "delete" -> {
                NSound.click(player);
            }
            case "category" -> {
                handleCategoryCommand(sender, executor, Arrays.copyOfRange(args, 1, args. length));
                NSound.click(player);
            }
            case "list" -> {
                NSound.click(player);
            }
            default -> sendUsage(sender);
        }
        NSound.click(player);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorUtil.hexColor("&e&l/contract category usage:"));
        sender.sendMessage(LanguageLoader.getMessage("contract-usage-create"));
        sender.sendMessage(LanguageLoader.getMessage("contract-usage-edit"));
        sender.sendMessage(LanguageLoader.getMessage("contract-usage-delete"));
        sender.sendMessage(LanguageLoader.getMessage("contract-usage-list"));
        sender.sendMessage(LanguageLoader.getMessage("contract-usage-category"));
    }

    private void handleCategoryCommand(CommandSender sender, Entity entity, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(ColorUtil.hexColor("&e&l/contract category usage:"));
            sender.sendMessage(LanguageLoader.getMessage("contract-usage-category-create"));
            sender.sendMessage(LanguageLoader.getMessage("contract-usage-category-edit"));
            sender.sendMessage(LanguageLoader.getMessage("contract-usage-category-delete"));
            sender.sendMessage(LanguageLoader.getMessage("contract-usage-category-list"));
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
                player.sendMessage(ColorUtil.hexColor("&7Please enter the &fcategory ID"));
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
                    if (NOrder.getInstance().getContractCategoryManager().getCategoryById(categoryId) != null) {
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
                        ContractCategory category = NOrder.getInstance().getContractCategoryManager()
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
                ContractCategory existingCategory = NOrder.getInstance().getContractCategoryManager()
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
                    boolean success = NOrder.getInstance().getContractCategoryManager()
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
                    ContractCategory category = NOrder.getInstance().getContractCategoryManager()
                            .getCategoryById(categoryID);

                    if (category == null) {
                        sender.sendMessage(ColorUtil.hexColor("&cCategory not found: " + categoryID));
                        if (entity instanceof Player player) {
                            NSound.error(player);
                        }
                        return;
                    }

                    boolean success = NOrder.getInstance().getContractCategoryManager()
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
                Collection<ContractCategory> categories = NOrder.getInstance().getContractCategoryManager()
                        .getAllCategories();

                if (categories.isEmpty()) {
                    sender.sendMessage(ColorUtil.hexColor("&eNo categories found."));
                    return;
                }

                sender.sendMessage(ColorUtil.hexColor("&6═══════ Categories ═══════"));
                for (ContractCategory category : categories) {
                    int orderCount = NOrder.getInstance().getContractManager().getContractsByCategory(category.getCategoryId()).size();
                    sender.sendMessage(ColorUtil.hexColor("&e" + category.getCategoryName() +
                            " &7(" + orderCount + " orders) &8[" + category.getCategoryId() + "]"));
                }
            }
            default -> {
                sender.sendMessage(ColorUtil.hexColor("&cUsage: /orderadmin category <create|delete|list>"));
            }
        }
    }

    @Override
    public List<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        List<String> suggestions = new ArrayList<>();

        // top-level options
        List<String> topOptions = Arrays.asList("category", "create", "delete", "edit", "list");

        String current;
        if (args.length > 0) {
            current = args[args.length - 1].toLowerCase();
        } else {
            current = "";
        }

        // no args typed yet -> suggest top-level options
        if (args.length == 0) {
            suggestions.addAll(topOptions);
            return suggestions;
        }

        // only first token typed -> suggest top-level options filtered
        if (args.length == 1) {
            for (String opt : topOptions) {
                if (opt.startsWith(current)) suggestions.add(opt);
            }
            return suggestions;
        }

        String first = args[0].toLowerCase();

        switch (first) {
            case "delete":
            case "edit": {
                // suggest contract ids for delete/edit
                List<String> ids = NOrder.getInstance().getContractManager()
                        .getAllContracts()
                        .stream()
                        .map(Contract::getId)
                        .filter(id -> id != null && id.toLowerCase().startsWith(current))
                        .collect(Collectors.toList());
                suggestions.addAll(ids);
                break;
            }
            case "list": {
                // suggest category ids for list
                List<String> catIds = NOrder.getInstance().getContractCategoryManager()
                        .getAllCategories()
                        .stream()
                        .map(ContractCategory::getCategoryId)
                        .filter(id -> id != null && id.toLowerCase().startsWith(current))
                        .collect(Collectors.toList());
                suggestions.addAll(catIds);
                break;
            }
            case "category": {
                List<String> catOptions = Arrays.asList("create", "edit", "delete", "list");
                // if second token is being typed (subcommand) -> suggest category suboptions
                if (args.length == 2) {
                    for (String opt : catOptions) {
                        if (opt.startsWith(current)) suggestions.add(opt);
                    }
                    return suggestions;
                }

                String sub = args[1].toLowerCase();
                if (("edit".equals(sub) || "delete".equals(sub)) && args.length >= 3) {
                    // suggest category ids for category edit/delete
                    List<String> catIds = NOrder.getInstance().getContractCategoryManager()
                            .getAllCategories()
                            .stream()
                            .map(ContractCategory::getCategoryId)
                            .filter(id -> id != null && id.toLowerCase().startsWith(current))
                            .collect(Collectors.toList());
                    suggestions.addAll(catIds);
                }
                break;
            }
            default:
                // no suggestions for other deeper arguments
                break;
        }

        return suggestions;
    }
}
