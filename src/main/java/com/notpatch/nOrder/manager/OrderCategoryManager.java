package com.notpatch.nOrder.manager;

import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.database.DatabaseManager;
import com.notpatch.nOrder.model.OrderCategory;
import com.notpatch.nlib.util.NLogger;
import lombok.Getter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrderCategoryManager {

    private final NOrder main;
    private final DatabaseManager databaseManager;

    @Getter
    private final Map<String, OrderCategory> categories = new ConcurrentHashMap<>();

    // Maps category ID to list of order IDs
    private final Map<String, List<String>> categoryOrders = new ConcurrentHashMap<>();

    public OrderCategoryManager(NOrder main) {
        this.main = main;
        this.databaseManager = main.getDatabaseManager();
    }

    public void loadCategories() {
        if (!databaseManager.isConnectionValid()) {
            NLogger.error("Database connection is null or invalid. Cannot load categories.");
            return;
        }

        String query = "SELECT * FROM order_categories";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            categories.clear();

            while (rs.next()) {
                String categoryId = rs.getString("category_id");
                String categoryName = rs.getString("category_name");
                String displayItem = rs.getString("display_item");
                LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();

                OrderCategory category = new OrderCategory(categoryId, categoryName, displayItem, createdAt);
                categories.put(categoryId, category);
            }

            NLogger.info("Loaded " + categories.size() + " categories.");

        } catch (SQLException e) {
            NLogger.error("Error loading categories: " + e.getMessage());
            e.printStackTrace();
        }

        loadCategoryOrders();
    }

    private void loadCategoryOrders() {
        categoryOrders.clear();

        // Only load admin orders from admin_orders table
        String query = "SELECT order_id, category_id FROM admin_orders WHERE category_id IS NOT NULL";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String orderId = rs.getString("order_id");
                String categoryId = rs.getString("category_id");

                categoryOrders.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(orderId);
            }

        } catch (SQLException e) {
            NLogger.error("Error loading admin category orders: " + e.getMessage());
        }
    }

    public void saveCategories() {
        if (!databaseManager.isConnectionValid()) {
            NLogger.error("Database connection is null or invalid. Cannot save categories.");
            return;
        }

        String query;
        if (databaseManager.isUsingSQLite()) {
            query = """
                INSERT INTO order_categories (category_id, category_name, display_item, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(category_id) DO UPDATE SET
                category_name = excluded.category_name,
                display_item = excluded.display_item
                """;
        } else {
            query = """
                INSERT INTO order_categories (category_id, category_name, display_item, created_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                category_name = VALUES(category_name),
                display_item = VALUES(display_item)
                """;
        }

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            for (OrderCategory category : categories.values()) {
                stmt.setString(1, category.getCategoryId());
                stmt.setString(2, category.getCategoryName());
                stmt.setString(3, category.getDisplayItem());
                stmt.setTimestamp(4, java.sql.Timestamp.valueOf(category.getCreatedAt()));
                stmt.addBatch();
            }

            stmt.executeBatch();
            NLogger.info("Saved " + categories.size() + " categories.");

        } catch (SQLException e) {
            NLogger.error("Error saving categories: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public OrderCategory createCategory(String categoryName, String displayItem) {
        String categoryId = UUID.randomUUID().toString();
        return createCategory(categoryId, categoryName, displayItem);
    }

    public OrderCategory createCategory(String categoryId, String categoryName, String displayItem) {
        // Check if category ID already exists
        if (categories.containsKey(categoryId)) {
            return null;
        }

        OrderCategory category = new OrderCategory(categoryId, categoryName, displayItem);
        categories.put(categoryId, category);

        // Save immediately
        saveCategories();

        return category;
    }

    public boolean updateCategory(String categoryId, String newCategoryName) {
        if (!categories.containsKey(categoryId)) {
            return false;
        }

        OrderCategory oldCategory = categories.get(categoryId);

        // Create new category with updated name
        OrderCategory updatedCategory = new OrderCategory(
                categoryId,
                newCategoryName,
                oldCategory.getDisplayItem(),
                oldCategory.getCreatedAt()
        );

        // Update in map
        categories.put(categoryId, updatedCategory);

        // Update in database
        String query = "UPDATE order_categories SET category_name = ? WHERE category_id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, newCategoryName);
            stmt.setString(2, categoryId);
            stmt.executeUpdate();

            return true;

        } catch (SQLException e) {
            NLogger.error("Error updating category name: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteCategory(String categoryId) {
        if (!categories.containsKey(categoryId)) {
            return false;
        }

        categories.remove(categoryId);
        categoryOrders.remove(categoryId);

        String query = "DELETE FROM order_categories WHERE category_id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, categoryId);
            stmt.executeUpdate();

            String updateAdminQuery = "UPDATE admin_orders SET category_id = NULL WHERE category_id = ?";
            try (PreparedStatement updateAdminStmt = conn.prepareStatement(updateAdminQuery)) {
                updateAdminStmt.setString(1, categoryId);
                updateAdminStmt.executeUpdate();
            }

            return true;

        } catch (SQLException e) {
            NLogger.error("Error deleting category: " + e.getMessage());
            return false;
        }
    }

    public OrderCategory getCategoryById(String categoryId) {
        return categories.get(categoryId);
    }

    public OrderCategory getCategoryByName(String categoryName) {
        return categories.values().stream()
                .filter(cat -> cat.getCategoryName().equalsIgnoreCase(categoryName))
                .findFirst()
                .orElse(null);
    }

    public List<com.notpatch.nOrder.model.AdminOrder> getAdminOrdersInCategory(String categoryId) {
        if (main.getAdminOrderManager() == null) {
            return new ArrayList<>();
        }

        return main.getAdminOrderManager().getAdminOrdersByCategory(categoryId);
    }

    public void assignOrderToCategory(String orderId, String categoryId) {
        // Remove from previous category if exists
        categoryOrders.values().forEach(list -> list.remove(orderId));

        // Add to new category
        if (categoryId != null) {
            categoryOrders.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(orderId);
        }

        // Update admin_orders table only
        String query = "UPDATE admin_orders SET category_id = ? WHERE order_id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, categoryId);
            stmt.setString(2, orderId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            NLogger.error("Error assigning admin order to category: " + e.getMessage());
        }
    }

    public Collection<OrderCategory> getAllCategories() {
        return categories.values();
    }
}