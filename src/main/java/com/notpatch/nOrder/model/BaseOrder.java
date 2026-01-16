package com.notpatch.nOrder.model;

import lombok.Data;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
public abstract class BaseOrder {

    protected final String id;
    protected final ItemStack item;
    protected final String customItemId;
    protected final int amount;
    protected final double price;
    protected int delivered;
    protected int collected;
    protected final LocalDateTime createdAt;
    protected final LocalDateTime expirationDate;
    protected final boolean highlight;
    protected OrderStatus status;

    public BaseOrder(String id, ItemStack item, String customItemId, int amount, double price,
                     int delivered, int collected, LocalDateTime createdAt, LocalDateTime expirationDate,
                     boolean highlight, OrderStatus status) {
        this.id = id;
        this.item = item;
        this.customItemId = customItemId;
        this.amount = amount;
        this.price = price;
        this.delivered = delivered;
        this.collected = collected;
        this.createdAt = createdAt;
        this.expirationDate = expirationDate;
        this.highlight = highlight;
        this.status = status;
    }

    public boolean isCustomItem() {
        return customItemId != null && !customItemId.isEmpty();
    }

    public int getRemaining() {
        return amount - delivered;
    }

    public void addDelivered(int quantity) {
        this.delivered += quantity;
        if (delivered > amount) {
            delivered = amount;
        }
    }

    public void addCollected(int quantity) {
        this.collected += quantity;
        if (collected > delivered) {
            collected = delivered;
        }
    }

    public Material getMaterial() {
        return item.getType();
    }

    public void removeDelivered(int quantity) {
        this.delivered -= quantity;
        if (this.delivered < 0) {
            this.delivered = 0;
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expirationDate);
    }

    public long getRemainingHours() {
        return Duration.between(LocalDateTime.now(), expirationDate).toHours();
    }

    /**
     * Get the display name for this order
     * @return The display name
     */
    public abstract String getDisplayName();

    /**
     * Check if this order can be fulfilled by a player
     * @return true if the order can be fulfilled
     */
    public abstract boolean canBeFulfilled();
}

