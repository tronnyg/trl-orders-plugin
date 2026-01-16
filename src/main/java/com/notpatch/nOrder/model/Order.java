package com.notpatch.nOrder.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class Order extends BaseOrder {

    private final UUID playerId;
    private final String playerName;

    public Order(String id, UUID playerId, String playerName, ItemStack item, String customItemId,
                 int amount, double price, int delivered, int collected, LocalDateTime createdAt,
                 LocalDateTime expirationDate, boolean highlight, OrderStatus status) {
        super(id, item, customItemId, amount, price, delivered, collected, createdAt, expirationDate, highlight, status);
        this.playerId = playerId;
        this.playerName = playerName;
    }

    @Override
    public String getDisplayName() {
        return playerName;
    }

    @Override
    public boolean canBeFulfilled() {
        return status == OrderStatus.ACTIVE && !isExpired();
    }

    public boolean isOwner(Player player) {
        return this.playerId.equals(player.getUniqueId()) || this.playerName.equalsIgnoreCase(player.getName());
    }


}
