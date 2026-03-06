package com.notpatch.nOrder.manager;

import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.model.Contract;
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

public class ContractManager {

    private final NOrder main;
    // Replace flat map with nested map keyed by categoryId -> (contractId -> Contract)
    private final Map<String, Map<String, Contract>> contractsByCategory = new ConcurrentHashMap<>();

    public ContractManager(NOrder main) {
        this.main = main;
    }

    /**
     * Load all contracts from the database (load once into memory grouped by category)
     */
    public void loadcontracts() {
        if (!main.getDatabaseManager().isConnectionValid()) {
            NLogger.error("Database connection is null. Cannot load contracts.");
            return;
        }

        try (Connection conn = main.getDatabaseManager().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM contracts")) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String contractId = rs.getString("contract_id");

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
                        NLogger.warn("Error parsing enchantments for contract " + contractId + ": " + e.getMessage());
                    }
                }

                int amount = rs.getInt("amount");
                double price = rs.getDouble("price");
                int delivered = rs.getInt("delivered");
                int collected = rs.getInt("collected");

                Timestamp expiresAtTs = rs.getTimestamp("expires_at");
                LocalDateTime expiresAt = expiresAtTs != null ? expiresAtTs.toLocalDateTime() : null;

                Timestamp createdAtTs = rs.getTimestamp("created_at");
                LocalDateTime createdAt = createdAtTs != null ? createdAtTs.toLocalDateTime() : null;

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

                // Skip archived or cancelled contracts
                if (status == OrderStatus.ARCHIVED || status == OrderStatus.CANCELLED) continue;

                Contract contract = new Contract(
                        contractId, item, customItemId, amount, price, delivered, collected,
                        createdAt, expiresAt, highlight, status, categoryId, customName,
                        cooldownDuration, repeatable, cooldownEndsAt, lastCompletedAt
                );

                // store in nested map
                contractsByCategory.computeIfAbsent(categoryId == null ? "" : categoryId, k -> new ConcurrentHashMap<>())
                        .put(contractId, contract);
            }

            // log total loaded
            int total = contractsByCategory.values().stream().mapToInt(Map::size).sum();
            NLogger.info("Loaded " + total + " contracts from database");

        } catch (SQLException e) {
            NLogger.error("Error loading contracts: " + e.getMessage());
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
     * Add a contract
     */
    public void addContract(Contract contract) {
        String categoryId = contract.getCategoryId() == null ? "" : contract.getCategoryId();
        contractsByCategory.computeIfAbsent(categoryId, k -> new ConcurrentHashMap<>())
                .put(contract.getId(), contract);
        saveContract(contract);
    }

    /**
     * Save a single contract to database (insert or update)
     */
    public void saveContract(Contract order) {
        if (!main.getDatabaseManager().isConnectionValid()) {
            NLogger.error("Database connection is null. Cannot save contract.");
            return;
        }

        String sql = """
                INSERT INTO contracts (contract_id, material, custom_item_id, enchantments, amount, price, delivered, collected, created_at, expires_at, highlight, status, category_id, custom_name, cooldown_duration, repeatable, cooldown_ends_at, last_completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                delivered = VALUES(delivered),
                collected = VALUES(collected),
                status = VALUES(status),
                cooldown_ends_at = VALUES(cooldown_ends_at),
                last_completed_at = VALUES(last_completed_at),
                material = VALUES(material),
                custom_item_id = VALUES(custom_item_id),
                enchantments = VALUES(enchantments),
                amount = VALUES(amount),
                price = VALUES(price),
                created_at = VALUES(created_at),
                highlight = VALUES(highlight),
                category_id = VALUES(category_id),
                custom_name = VALUES(custom_name),
                cooldown_duration = VALUES(cooldown_duration),
                repeatable = VALUES(repeatable)
                """;

        if (main.getDatabaseManager().isUsingSQLite()) {
            sql = """
                    INSERT OR REPLACE INTO contracts (contract_id, material, custom_item_id, enchantments, amount, price, delivered, collected, created_at, expires_at, highlight, status, category_id, custom_name, cooldown_duration, repeatable, cooldown_ends_at, last_completed_at)
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

            if (order.getExpirationDate() != null) {
                stmt.setTimestamp(10, Timestamp.valueOf(order.getExpirationDate()));
            } else {
                stmt.setNull(10, Types.TIMESTAMP);
            }

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
            NLogger.error("Failed to save contract: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Remove a contract (from memory then DB)
     */
    public boolean removeContract(String contractId) {
        // remove from nested map
        boolean removed = false;
        Iterator<Map.Entry<String, Map<String, Contract>>> it = contractsByCategory.entrySet().iterator();
        while (it.hasNext()) {
            Map<String, Contract> inner = it.next().getValue();
            if (inner.remove(contractId) != null) {
                removed = true;
                // cleanup empty category map
                if (inner.isEmpty()) {
                    it.remove();
                }
                break;
            }
        }

        if (!removed) return false;

        try (Connection conn = main.getDatabaseManager().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM contracts WHERE contract_id = ?")) {

            stmt.setString(1, contractId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            NLogger.error("Failed to remove contract from database: " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Get contract by ID
     */
    public Contract getContractById(String contractId) {
        for (Map<String, Contract> inner : contractsByCategory.values()) {
            Contract c = inner.get(contractId);
            if (c != null) return c;
        }
        return null;
    }

    /**
     * Get all contracts
     */
    public List<Contract> getAllContracts() {
        return contractsByCategory.values().stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList());
    }

    /**
     * Get active contracts (can be fulfilled)
     */
    public List<Contract> getActiveContracts() {
        return contractsByCategory.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(Contract::canBeFulfilled)
                .collect(Collectors.toList());
    }

    /**
     * Get contracts by category
     */
    public List<Contract> getContractsByCategory(String categoryId) {
        Map<String, Contract> inner = contractsByCategory.get(categoryId == null ? "" : categoryId);
        if (inner == null) return Collections.emptyList();
        return new ArrayList<>(inner.values());
    }

    /**
     * Get contracts in cooldown
     */
    public List<Contract> getContractsInCooldown() {
        return contractsByCategory.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(Contract::isInCooldown)
                .collect(Collectors.toList());
    }

    /**
     * Update contract status in database (single-record)
     */
    private void updateContractStatusInDatabase(Contract order) {
        String sql = "UPDATE contracts SET status = ?, cooldown_ends_at = ?, last_completed_at = ? WHERE contract_id = ?";

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
            NLogger.error("Failed to update contract status in database: " + e.getMessage());
        }
    }

    /**
     * Generate random contract ID
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
     * Process contracts cooldowns and resume if needed
     */
    public void processCooldowns() {
        List<Contract> contractsToResume = contractsByCategory.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(Contract::shouldResume)
                .collect(Collectors.toList());

        for (Contract order : contractsToResume) {
            order.resumeAfterCooldown();
            updateContractStatusInDatabase(order);
            NLogger.info("Resumed contract " + order.getId() + " after cooldown");
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
     * Clean expired contracts
     */
    public void cleanExpiredcontracts() {
        List<Contract> expiredContracts = contractsByCategory.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(order -> order.isExpired() && !order.isRepeatable())
                .collect(Collectors.toList());

        for (Contract order : expiredContracts) {
            // Call complete() which handles cooldown for repeatable contracts
            order.complete();
            updateContractStatusInDatabase(order);
        }

        if (!expiredContracts.isEmpty()) {
            NLogger.info("Cleaned " + expiredContracts.size() + " expired contracts");
        }
    }

    /**
     * Start cleanup task for expired contracts
     */
    public void startCleanupTask() {
        main.getMorePaperLib().scheduling().asyncScheduler().runAtFixedRate(
                this::cleanExpiredcontracts,
                Duration.ofMinutes(10),
                Duration.ofMinutes(10)
        );
    }

    /**
     * Save all contracts from the in-memory hashmap to the database in a single transaction.
     * This is intended to be called on plugin disable as a final sync (fallback).
     */
    public void saveContracts() {
        if (!main.getDatabaseManager().isConnectionValid()) {
            NLogger.error("Database connection is null. Cannot save contracts.");
            return;
        }

        String sql;
        if (main.getDatabaseManager().isUsingSQLite()) {
            sql = """
                    INSERT OR REPLACE INTO contracts (contract_id, material, custom_item_id, enchantments, amount, price, delivered, collected, created_at, expires_at, highlight, status, category_id, custom_name, cooldown_duration, repeatable, cooldown_ends_at, last_completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        } else {
            sql = """
                    INSERT INTO contracts (contract_id, material, custom_item_id, enchantments, amount, price, delivered, collected, created_at, expires_at, highlight, status, category_id, custom_name, cooldown_duration, repeatable, cooldown_ends_at, last_completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    delivered = VALUES(delivered),
                    collected = VALUES(collected),
                    status = VALUES(status),
                    cooldown_ends_at = VALUES(cooldown_ends_at),
                    last_completed_at = VALUES(last_completed_at),
                    material = VALUES(material),
                    custom_item_id = VALUES(custom_item_id),
                    enchantments = VALUES(enchantments),
                    amount = VALUES(amount),
                    price = VALUES(price),
                    created_at = VALUES(created_at),
                    highlight = VALUES(highlight),
                    category_id = VALUES(category_id),
                    custom_name = VALUES(custom_name),
                    cooldown_duration = VALUES(cooldown_duration),
                    repeatable = VALUES(repeatable)
                    """;
        }

        try (Connection conn = main.getDatabaseManager().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (Map<String, Contract> inner : contractsByCategory.values()) {
                for (Contract order : inner.values()) {
                    int idx = 1;
                    stmt.setString(idx++, order.getId());
                    stmt.setString(idx++, order.getMaterial().name());
                    stmt.setString(idx++, order.getCustomItemId());

                    String enchantments = formatEnchantments(order.getItem());
                    if (enchantments != null) stmt.setString(idx++, enchantments); else stmt.setNull(idx++, Types.VARCHAR);

                    stmt.setInt(idx++, order.getAmount());
                    stmt.setDouble(idx++, order.getPrice());
                    stmt.setInt(idx++, order.getDelivered());
                    stmt.setInt(idx++, order.getCollected());

                    if (order.getCreatedAt() != null) stmt.setTimestamp(idx++, Timestamp.valueOf(order.getCreatedAt()));
                    else stmt.setNull(idx++, Types.TIMESTAMP);

                    if (order.getExpirationDate() != null) stmt.setTimestamp(idx++, Timestamp.valueOf(order.getExpirationDate()));
                    else stmt.setNull(idx++, Types.TIMESTAMP);

                    stmt.setBoolean(idx++, order.isHighlight());
                    stmt.setString(idx++, order.getStatus().name());
                    stmt.setString(idx++, order.getCategoryId());
                    stmt.setString(idx++, order.getCustomName());
                    stmt.setLong(idx++, order.getCooldownDuration());
                    stmt.setBoolean(idx++, order.isRepeatable());

                    if (order.getCooldownEndsAt() != null) stmt.setTimestamp(idx++, Timestamp.valueOf(order.getCooldownEndsAt()));
                    else stmt.setNull(idx++, Types.TIMESTAMP);

                    if (order.getLastCompletedAt() != null) stmt.setTimestamp(idx++, Timestamp.valueOf(order.getLastCompletedAt()));
                    else stmt.setNull(idx++, Types.TIMESTAMP);

                    stmt.addBatch();
                }
            }

            stmt.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            NLogger.error("Failed to save all contracts: " + e.getMessage());
            e.printStackTrace();
            // best-effort: attempt no further action here; caller should handle shutdown cleanup
        } finally {
            // ensure connection auto-commit reset is handled by try-with-resources closing the connection
        }
    }
}
