package com.notpatch.nOrder.manager;

import com.notpatch.nOrder.gui.NewOrderMenu;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NewOrderMenuManager {

    private final Map<UUID, NewOrderMenu> activeMenus = new HashMap<>();

    public NewOrderMenu getOrCreateMenu(Player player) {
        return activeMenus.computeIfAbsent(player.getUniqueId(), uuid -> new NewOrderMenu());
    }

    public NewOrderMenu getMenu(Player player) {
        return activeMenus.get(player.getUniqueId());
    }

    public void removeMenu(Player player) {
        activeMenus.remove(player.getUniqueId());
    }

    public void clearAll() {
        activeMenus.clear();
    }
}

