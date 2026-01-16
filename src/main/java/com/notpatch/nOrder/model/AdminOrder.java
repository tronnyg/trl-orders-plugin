package com.notpatch.nOrder.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrder extends BaseOrder {

    private final String categoryId;
    private final String customName;
    private final long cooldownDuration; // in seconds
    private final boolean repeatable;
    private LocalDateTime cooldownEndsAt;
    private LocalDateTime lastCompletedAt;

    public AdminOrder(String id, ItemStack item, String customItemId, int amount, double price,
                      int delivered, int collected, LocalDateTime createdAt, LocalDateTime expirationDate,
                      boolean highlight, OrderStatus status, String categoryId, String customName,
                      long cooldownDuration, boolean repeatable, LocalDateTime cooldownEndsAt,
                      LocalDateTime lastCompletedAt) {
        super(id, item, customItemId, amount, price, delivered, collected, createdAt, expirationDate, highlight, status);
        this.categoryId = categoryId;
        this.customName = customName;
        this.cooldownDuration = cooldownDuration;
        this.repeatable = repeatable;
        this.cooldownEndsAt = cooldownEndsAt;
        this.lastCompletedAt = lastCompletedAt;
    }

    @Override
    public String getDisplayName() {
        return customName != null && !customName.isEmpty() ? customName : getMaterial().name();
    }

    @Override
    public boolean canBeFulfilled() {
        // Cannot fulfill if in cooldown
        if (isInCooldown()) {
            return false;
        }

        // Cannot fulfill if expired
        if (isExpired()) {
            return false;
        }

        // Cannot fulfill if not active
        if (status != OrderStatus.ACTIVE) {
            return false;
        }

        return true;
    }

    /**
     * Check if the order is currently in cooldown
     * @return true if in cooldown
     */
    public boolean isInCooldown() {
        if (cooldownEndsAt == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(cooldownEndsAt);
    }

    /**
     * Get the remaining cooldown time in seconds
     * @return remaining cooldown seconds, or 0 if not in cooldown
     */
    public long getRemainingCooldownSeconds() {
        if (!isInCooldown()) {
            return 0;
        }
        return Duration.between(LocalDateTime.now(), cooldownEndsAt).getSeconds();
    }

    /**
     * Start the cooldown period after order completion or expiration
     */
    public void startCooldown() {
        this.lastCompletedAt = LocalDateTime.now();
        this.cooldownEndsAt = LocalDateTime.now().plusSeconds(cooldownDuration);
        this.status = OrderStatus.COOLDOWN;
    }

    /**
     * Resume the order after cooldown ends (if repeatable)
     */
    public void resumeAfterCooldown() {
        if (!repeatable) {
            this.status = OrderStatus.COMPLETED;
            return;
        }

        // Reset progress
        this.delivered = 0;
        this.collected = 0;
        this.cooldownEndsAt = null;
        this.status = OrderStatus.ACTIVE;
    }

    /**
     * Check if the order should be resumed (cooldown ended and is repeatable)
     * @return true if should resume
     */
    public boolean shouldResume() {
        return repeatable && !isInCooldown() && status == OrderStatus.COOLDOWN;
    }

    /**
     * Handle order completion or expiration
     */
    public void complete() {
        if (repeatable && cooldownDuration > 0) {
            startCooldown();
        } else {
            this.status = OrderStatus.COMPLETED;
        }
    }

    /**
     * Get remaining cooldown time formatted as hours and minutes
     * @return formatted cooldown time
     */
    public String getFormattedCooldownTime() {
        long seconds = getRemainingCooldownSeconds();
        if (seconds <= 0) {
            return "0m";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }
}

