package com.notpatch.nOrder.command;

import com.notpatch.nOrder.LanguageLoader;
import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.gui.CategoryOrdersMenu;
import com.notpatch.nOrder.model.OrderCategory;
import io.papermc.paper.command.brigadier.BasicCommand;
import com.notpatch.nlib.effect.NSound;
import com.notpatch.nlib.util.ColorUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class OrderCategoryCommand implements BasicCommand {

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        Entity executor = commandSourceStack.getExecutor();

        if (!(executor instanceof Player player)) {
            commandSourceStack. getSender().sendMessage("This command can only be used by players.");
            return;
        }

        if (args.length == 0) {
            player.sendMessage(ColorUtil.hexColor("&cUsage: /ordercategory <category_id>"));
            NSound.error(player);
            return;
        }

        String categoryID = String.join(" ", args);
        OrderCategory category = NOrder.getInstance().getOrderCategoryManager().getCategoryById(categoryID);

        if (category == null) {
            player.sendMessage(ColorUtil.hexColor(LanguageLoader.getMessage("category-not-found")
                    .replace("%category%", categoryID)));
            NSound.error(player);
            return;
        }

        new CategoryOrdersMenu(category, player).open(player);
        NSound.click(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (args.length == 0) {
            // Return all category IDs
            return NOrder.getInstance().getOrderCategoryManager().getAllCategories().stream()
                    .map(OrderCategory::getCategoryId)
                    .collect(Collectors.toList());
        } else if (args.length == 1) {
            // Filter category IDs based on input
            String input = args[0].toLowerCase();
            return NOrder.getInstance().getOrderCategoryManager().getAllCategories().stream()
                    .map(OrderCategory::getCategoryId)
                    .filter(id -> id.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}