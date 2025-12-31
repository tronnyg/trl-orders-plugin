package com.notpatch.nOrder.hook;

import com.notpatch.nOrder.Settings;
import lombok.Getter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public class LuckPermsHook {

    private LuckPerms api;
    @Getter
    private boolean isAvailable;

    public LuckPermsHook() {
        try {
            RegisteredServiceProvider<LuckPerms> rsp = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (rsp != null) {
                this.api = rsp.getProvider();
                this.isAvailable = true;
            } else {
                this.isAvailable = false;
            }
        } catch (Throwable t) {
            this.isAvailable = false;
        }
    }

    public int getOrderLimit(Player player, int defaultLimit) {
        return getPermissionValue(player, Settings.ORDER_LIMIT_PERMISSION, defaultLimit);
    }

    public int getOrderExpiration(Player player, int defaultLimit) {
        return getPermissionValue(player, Settings.ORDER_EXPIRATION_PERMISSION, defaultLimit);
    }

    private int getPermissionValue(Player player, String permissionPrefix, int defaultValue) {
        if (!isAvailable || api == null) {
            return findByBukkit(player, permissionPrefix, defaultValue);
        }

        try {
            UUID uuid = player.getUniqueId();
            User user = api.getUserManager().getUser(uuid);

            if (user == null) {
                user = api.getUserManager().loadUser(uuid).join();
            }

            if (user == null) {
                return findByBukkit(player, permissionPrefix, defaultValue);
            }

            QueryOptions queryOptions = api.getContextManager().getQueryOptions(user).orElse(
                    api.getContextManager().getStaticQueryOptions()
            );

            for (int i = 100; i >= 1; i--) {
                String node = permissionPrefix + "." + i;
                try {
                    boolean has = user.getCachedData().getPermissionData(queryOptions).checkPermission(node).asBoolean();
                    if (has) return i;
                } catch (Throwable e) {
                    // Fallback to Bukkit permission
                    if (player.hasPermission(node)) return i;
                }
            }
        } catch (Throwable e) {
            return findByBukkit(player, permissionPrefix, defaultValue);
        }

        return defaultValue;
    }

    private int findByBukkit(Player player, String permissionPrefix, int defaultValue) {
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission(permissionPrefix + "." + i)) return i;
        }
        return defaultValue;
    }
}