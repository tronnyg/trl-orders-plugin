package com.notpatch.nOrder.manager;

import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.model.AdminOrder;
import com.notpatch.nOrder.model.OrderStatus;
import com.notpatch.nlib.util.NLogger;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class AdminOrderManager {

    private final NOrder main;
    private final Map<String, AdminOrder> adminOrders = new ConcurrentHashMap<>();

    public AdminOrderManager(NOrder main) {
        this.main = main;
    }

    /**
     * Load all admin orders from the database
     */
    public void loadAdminOrders() {
        if (!main.getDatabaseManager().isConnectionValid()) {
            NLogger.error("Database connection is null. Cannot load admin orders.");
            return;
        }

        try (Connection conn = main.getDatabaseManager().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM admin_orders")) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String orderId = rs.getString("order_id");

                String materialName = rs.getString("material");
                Material material = Material.valueOf(materialName);
                ItemStack item = new ItemStack(material);

                String customItemId = rs.getString("custom_item_id");

                // Parse enchantments
                String enchantmentsStr = rs.getString("enchantments");
                if (enchantmentsStr != null && !enchantmentsStr.isEmpty()) {
                    try {
                        String[] enchantPairs = enchantmentsStr.split(",");
                        for (String pair : enchantPairs) {
                            String[] parts = pair.split(":");
                            if (parts.length == 2) {
                                Enchantment enchant = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(parts[0]));
                                int level = Integer.parseInt(parts[1]);
                                if (enchant != null) {
                                    if (material == Material.ENCHANTED_BOOK) {
                                        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
                                        if (meta != null) {
                                            meta.addStoredEnchant(enchant, level, true);
                                            item.setItemMeta(meta);
                                        }
                                    } else {
                                        item.addEnchantment(enchant, level);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        NLogger.warn("Error parsing enchantments for admin order " + orderId + ": " + e.getMessage());
                    }
                }

                int amount = rs.getInt("amount");
                double price = rs.getDouble("price");
                int delivered = rs.getInt("delivered");
                int collected = rs.getInt("collected");
                LocalDateTime expiresAt = rs.getTimestamp("expires_at").toLocalDateTime();
                LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                boolean highlight = rs.getBoolean("highlight");
                String statusStr = rs.getString("status");
                OrderStatus status = OrderStatus.valueOf(statusStr);

                String categoryId = rs.getString("category_id");
                String customName = rs.getString("custom_name");
                long cooldownDuration = rs.getLong("cooldown_duration");
                boolean repeatable = rs.getBoolean("repeatable");

                Timestamp cooldownEndsAtTs = rs.getTimestamp("cooldown_ends_at");
                LocalDateTime cooldownEndsAt = cooldownEndsAtTs != null ? cooldownEndsAtTs.toLocalDateTime() : null;

                Timestamp lastCompletedAtTs = rs.getTimestamp("last_completed_at");
                LocalDateTime lastCompletedAt = lastCompletedAtTs != null ? lastCompletedAtTs.toLocalDateTime() : null;

                // Skip archived or cancelled orders
                if (status == OrderStatus.ARCHIVED || status == OrderStatus.CANCELLED) continue;

                AdminOrder order = new AdminOrder(
                        orderId, item, customItemId, amount, price, delivered, collected,
                        createdAt, expiresAt, highlight, status, categoryId, customName,
                        cooldownDuration, repeatable, cooldownEndsAt, lastCompletedAt
                );

                adminOrders.put(orderId, order);
            }

            NLogger.info("Loaded " + adminOrders.size() + " admin orders from database");

        } catch (SQLException e) {
            NLogger.error("Error loading admin orders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save all admin orders to the database
     */
    public void saveAdminOrders() {
        if (!main.getDatabaseManager().isConnectionValid()) {
            NLogger.error("Database connection is null. Cannot save admin orders.");
            return;
        }

        String sql = """
                INSERT INTO admin_orders (order_id, material, custom_item_id, enchantments, amount, price, delivered, collected, created_at, expires_at, highlight, status, category_id, custom_name, cooldown_duration, repeatable, cooldown_ends_at, last_completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                delivered = VALUES(delivered),
                collected = VALUES(collected),
                status = VALUES(status),
                cooldown_ends_at = VALUES(cooldown_ends_at),
                last_completed_at = VALUES(last_completed_at)
                """;

        if (main.getDatabaseManager().isUsingSQLite()) {
            sql = """
                    INSERT OR REPLACE INTO admin_orders (order_id, material, custom_item_id, enchantments, amount, price, delivered, collected, created_at, expires_at, highlight, status, category_id, custom_name, cooldown_duration, repeatable, cooldown_ends_at, last_completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        }

        try (Connection conn = main.getDatabaseManager().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (AdminOrder order : adminOrders.values()) {
                stmt.setString(1, order.getId());
                stmt.setString(2, order.getMaterial().name());
                stmt.setString(3, order.getCustomItemId());

                String enchantments = formatEnchantments(order.getItem());
                stmt.setString(4, enchantments);

                stmt.setInt(5, order.getAmount());
                stmt.setDouble(6, order.getPrice());
                stmt.setInt(7, order.getDelivered());
                stmt.setInt(8, order.getCollected());
                stmt.setTimestamp(9, Timestamp.valueOf(order.getCreatedAt()));
                stmt.setTimestamp(10, Timestamp.valueOf(order.getExpirationDate()));
                stmt.setBoolean(11, order.isHighlight());
                stmt.setString(12, order.getStatus().name());
                stmt.setString(13, order.getCategoryId());
                stmt.setString(14, order.getCustomName());
                stmt.setLong(15, order.getCooldownDuration());
                stmt.setBoolean(16, order.isRepeatable());

                if (order.getCooldownEndsAt() != null) {
                    stmt.setTimestamp(17, Timestamp.valueOf(order.getCooldownEndsAt()));
                } else {
                    stmt.setNull(17, Types.TIMESTAMP);
                }

                if (order.getLastCompletedAt() != null) {
                    stmt.setTimestamp(18, Timestamp.valueOf(order.getLastCompletedAt()));
                } else {
                    stmt.setNull(18, Types.TIMESTAMP);
                }

                stmt.addBatch();
            }

            stmt.executeBatch();
            NLogger.info("Saved " + adminOrders.size() + " admin orders to database");

        } catch (SQLException e) {
            NLogger.error("Failed to save admin orders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Format enchantments for database storage
     */
    private String formatEnchantments(ItemStack item) {
        if (item == null) return null;

        Map<Enchantment, Integer> enchants;

        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            enchants = meta.getStoredEnchants();
        } else {
            enchants = item.getEnchantments();
        }

        if (enchants.isEmpty()) return null;

        StringJoiner joiner = new StringJoiner(",");
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            joiner.add(entry.getKey().getKey().getKey() + ":" + entry.getValue());
        }

        return joiner.toString();
    }

    /**
     * Add a new admin order
     */
    public void addAdminOrder(AdminOrder order) {
        adminOrders.put(order.getId(), order);
        saveAdminOrder(order);
    }

    /**
     * Save a single admin order to database
     */
    private void saveAdminOrder(AdminOrder order) {
        if (!main.getDatabaseManager().isConnectionValid()) {
            NLogger.error("Database connection is null. Cannot save admin order.");
            return;
        }

        String sql = """
                INSERT INTO admin_orders (order_id, material, custom_item_id, enchantments, amount, price, delivered, collected, created_at, expires_at, highlight, status, category_id, custom_name, cooldown_duration, repeatable, cooldown_ends_at, last_completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                delivered = VALUES(delivered),
                collected = VALUES(collected),
                status = VALUES(status),
                cooldown_ends_at = VALUES(cooldown_ends_at),
                last_completed_at = VALUES(last_completed_at)
                """;

        if (main.getDatabaseManager().isUsingSQLite()) {
            sql = """
                    INSERT OR REPLACE INTO admin_orders (order_id, material, custom_item_id, enchantments, amount, price, delivered, collected, created_at, expires_at, highlight, status, category_id, custom_name, cooldown_duration, repeatable, cooldown_ends_at, last_completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        }

        try (Connection conn = main.getDatabaseManager().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, order.getId());
            stmt.setString(2, order.getMaterial().name());
            stmt.setString(3, order.getCustomItemId());

            String enchantments = formatEnchantments(order.getItem());
            stmt.setString(4, enchantments);

            stmt.setInt(5, order.getAmount());
            stmt.setDouble(6, order.getPrice());
            stmt.setInt(7, order.getDelivered());
            stmt.setInt(8, order.getCollected());
            stmt.setTimestamp(9, Timestamp.valueOf(order.getCreatedAt()));
            stmt.setTimestamp(10, Timestamp.valueOf(order.getExpirationDate()));
            stmt.setBoolean(11, order.isHighlight());
            stmt.setString(12, order.getStatus().name());
            stmt.setString(13, order.getCategoryId());
            stmt.setString(14, order.getCustomName());
            stmt.setLong(15, order.getCooldownDuration());
            stmt.setBoolean(16, order.isRepeatable());

            if (order.getCooldownEndsAt() != null) {
                stmt.setTimestamp(17, Timestamp.valueOf(order.getCooldownEndsAt()));
            } else {
                stmt.setNull(17, Types.TIMESTAMP);
            }

            if (order.getLastCompletedAt() != null) {
                stmt.setTimestamp(18, Timestamp.valueOf(order.getLastCompletedAt()));
            } else {
                stmt.setNull(18, Types.TIMESTAMP);
            }

            stmt.executeUpdate();

        } catch (SQLException e) {
            NLogger.error("Failed to save admin order: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Remove an admin order
     */
    public boolean removeAdminOrder(String orderId) {
        AdminOrder removed = adminOrders.remove(orderId);

        if (removed == null) return false;

        try (Connection conn = main.getDatabaseManager().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM admin_orders WHERE order_id = ?")) {

            stmt.setString(1, orderId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            NLogger.error("Failed to remove admin order from database: " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Get admin order by ID
     */
    public AdminOrder getAdminOrderById(String orderId) {
        return adminOrders.get(orderId);
    }

    /**
     * Get all admin orders
     */
    public List<AdminOrder> getAllAdminOrders() {
        return new ArrayList<>(adminOrders.values());
    }

    /**
     * Get active admin orders (can be fulfilled)
     */
    public List<AdminOrder> getActiveAdminOrders() {
        return adminOrders.values().stream()
                .filter(AdminOrder::canBeFulfilled)
                .collect(Collectors.toList());
    }

    /**
     * Get admin orders by category
     */
    public List<AdminOrder> getAdminOrdersByCategory(String categoryId) {
        return adminOrders.values().stream()
                .filter(order -> categoryId.equals(order.getCategoryId()))
                .collect(Collectors.toList());
    }

    /**
     * Get admin orders in cooldown
     */
    public List<AdminOrder> getAdminOrdersInCooldown() {
        return adminOrders.values().stream()
                .filter(AdminOrder::isInCooldown)
                .collect(Collectors.toList());
    }

    /**
     * Update admin order status in database
     */
    private void updateAdminOrderStatusInDatabase(AdminOrder order) {
        String sql = "UPDATE admin_orders SET status = ?, cooldown_ends_at = ?, last_completed_at = ? WHERE order_id = ?";

        try (Connection conn = main.getDatabaseManager().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, order.getStatus().name());

            if (order.getCooldownEndsAt() != null) {
                stmt.setTimestamp(2, Timestamp.valueOf(order.getCooldownEndsAt()));
            } else {
                stmt.setNull(2, Types.TIMESTAMP);
            }

            if (order.getLastCompletedAt() != null) {
                stmt.setTimestamp(3, Timestamp.valueOf(order.getLastCompletedAt()));
            } else {
                stmt.setNull(3, Types.TIMESTAMP);
            }

            stmt.setString(4, order.getId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            NLogger.error("Failed to update admin order status in database: " + e.getMessage());
        }
    }

    /**
     * Generate random order ID
     */
    public String createRandomId() {
        int length = 6;
        final String chars = "0123456789";
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Process admin order cooldowns and resume if needed
     */
    public void processCooldowns() {
        List<AdminOrder> ordersToResume = adminOrders.values().stream()
                .filter(AdminOrder::shouldResume)
                .collect(Collectors.toList());

        for (AdminOrder order : ordersToResume) {
            order.resumeAfterCooldown();
            updateAdminOrderStatusInDatabase(order);
            NLogger.info("Resumed admin order " + order.getId() + " after cooldown");
        }
    }

    /**
     * Start cooldown processing task
     */
    public void startCooldownTask() {
        main.getMorePaperLib().scheduling().asyncScheduler().runAtFixedRate(
                this::processCooldowns,
                Duration.ofMinutes(1),
                Duration.ofMinutes(1)
        );
    }

    /**
     * Clean expired admin orders
     */
    public void cleanExpiredAdminOrders() {
        List<AdminOrder> expiredOrders = adminOrders.values().stream()
                .filter(order -> order.isExpired() && !order.isRepeatable())
                .collect(Collectors.toList());

        for (AdminOrder order : expiredOrders) {
            // Call complete() which handles cooldown for repeatable orders
            order.complete();
            updateAdminOrderStatusInDatabase(order);
        }

        if (!expiredOrders.isEmpty()) {
            NLogger.info("Cleaned " + expiredOrders.size() + " expired admin orders");
        }
    }

    /**
     * Start cleanup task for expired orders
     */
    public void startCleanupTask() {
        main.getMorePaperLib().scheduling().asyncScheduler().runAtFixedRate(
                this::cleanExpiredAdminOrders,
                Duration.ofMinutes(10),
                Duration.ofMinutes(10)
        );
    }
}

