package com.notpatch.nOrder.util;

import com.notpatch.nOrder.LanguageLoader;
import com.notpatch.nOrder.Settings;
import com.notpatch.nOrder.model.AdminOrder;
import com.notpatch.nOrder.model.BaseOrder;
import com.notpatch.nOrder.model.Order;
import com.notpatch.nOrder.model.ProgressBar;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StringUtil {

    public static String replaceOrderPlaceholders(String text, BaseOrder order) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Settings.DATE_FORMAT);
        String countdown = "";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = order.getExpirationDate();
        if (now.isBefore(expireAt)) {
            Duration duration = Duration.between(now, expireAt);
            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;
            countdown = String.format(LanguageLoader.getMessage("order-countdown-format"), days, hours, minutes, seconds);
        }

        // Calculate cooldown time for AdminOrder
        String cooldownTime = "";
        if (order instanceof AdminOrder adminOrder) {
            if (adminOrder.isInCooldown()) {
                long remainingSeconds = adminOrder.getRemainingCooldownSeconds();
                long days = remainingSeconds / 86400;
                long hours = (remainingSeconds % 86400) / 3600;
                long minutes = (remainingSeconds % 3600) / 60;
                long seconds = remainingSeconds % 60;
                cooldownTime = String.format(LanguageLoader.getMessage("order-countdown-format"), days, hours, minutes, seconds);
            }
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && order instanceof Order playerOrder) {
            text = PlaceholderAPI.setPlaceholders(Bukkit.getPlayer(playerOrder.getPlayerId()), text);
        }

        return text
                .replace("%item%", formatMaterialName(order.getMaterial()))
                .replace("%material%", formatMaterialName(order.getMaterial()))
                .replace("%quantity%", String.valueOf(order.getAmount()))
                .replace("%highlighted%", order.isHighlight() ? LanguageLoader.getMessage("highlighted-yes") : LanguageLoader.getMessage("highlighted-no"))
                .replace("%amount%", String.valueOf(order.getAmount()))
                .replace("%delivered%", String.valueOf(order.getDelivered()))
                .replace("%remaining%", String.valueOf(order.getRemaining()))
                .replace("%price%", NumberFormatter.format(order.getPrice()))
                .replace("%total_price%", NumberFormatter.format(order.getPrice() * order.getAmount()))
                .replace("%paid_price%", NumberFormatter.format(order.getPrice() * order.getDelivered()))
                .replace("%created_at%", order.getCreatedAt().format(formatter))
                .replace("%expire_at%", order.getExpirationDate().format(formatter))
                .replace("%order_id%", order.getId())
                .replace("%time_remaining%", countdown)
                .replace("%cooldown_time%", cooldownTime)
                .replace("%progress_bar%", new ProgressBar(order).render())
                .replace("%ordered_by%", order.getDisplayName());
    }

    public static String formatMaterialName(Material material) {
        String materialName = material.name().toLowerCase().replace('_', ' ');
        StringBuilder builder = new StringBuilder();
        for (String parts : materialName.split(" ")) {
            if (parts.isEmpty()) continue;
            builder.append(Character.toUpperCase(parts.charAt(0)));
            if (parts.length() > 1) builder.append(parts.substring(1));
            builder.append(' ');
        }
        return builder.toString().trim();
    }
}
