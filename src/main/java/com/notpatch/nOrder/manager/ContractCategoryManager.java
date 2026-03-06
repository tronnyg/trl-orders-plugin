package com.notpatch.nOrder.manager;

import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.database.DatabaseManager;
import com.notpatch.nOrder.model.ContractCategory;
import com.notpatch.nlib.util.NLogger;
import lombok.Getter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ContractCategoryManager {

    private final NOrder main;
    private final DatabaseManager databaseManager;

    @Getter
    private final Map<String, ContractCategory> categories = new ConcurrentHashMap<>();

    public ContractCategoryManager(NOrder main) {
        this.main = main;
        this.databaseManager = main.getDatabaseManager();
    }

    public void loadCategories() {
        if (!databaseManager.isConnectionValid()) {
            NLogger.error("Database connection is null or invalid. Cannot load categories.");
            return;
        }

        String query = "SELECT * FROM contract_categories";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            categories.clear();

            while (rs.next()) {
                String categoryId = rs.getString("category_id");
                String categoryName = rs.getString("category_name");
                String displayItem = rs.getString("display_item");
                LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();

                ContractCategory category = new ContractCategory(categoryId, categoryName, displayItem, createdAt);
                categories.put(categoryId, category);
            }

            NLogger.info("Loaded " + categories.size() + " categories.");

        } catch (SQLException e) {
            NLogger.error("Error loading categories: " + e.getMessage());
        }
    }

    public ContractCategory createCategory(String categoryId, String categoryName, String displayItem) {
        // Check if category ID already exists
        if (categories.containsKey(categoryId)) {
            return null;
        }

        ContractCategory category = new ContractCategory(categoryId, categoryName, displayItem);
        categories.put(categoryId, category);

        // If DB connection isn't valid, keep it in-memory but log and return
        if (!databaseManager.isConnectionValid()) {
            NLogger.error("Database connection is null or invalid. Category will be kept in-memory only.");
            return category;
        }

        // Insert only the newly created category into the database (use upsert semantics)
        String query;
        if (databaseManager.isUsingSQLite()) {
            query = """
                INSERT INTO contract_categories (category_id, category_name, display_item, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(category_id) DO UPDATE SET
                category_name = excluded.category_name,
                display_item = excluded.display_item
                """;
        } else {
            query = """
                INSERT INTO contract_categories (category_id, category_name, display_item, created_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                category_name = VALUES(category_name),
                display_item = VALUES(display_item)
                """;
        }

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, category.getCategoryId());
            stmt.setString(2, category.getCategoryName());
            stmt.setString(3, category.getDisplayItem());
            stmt.setTimestamp(4, java.sql.Timestamp.valueOf(category.getCreatedAt()));

            stmt.executeUpdate();
            return category;

        } catch (SQLException e) {
            NLogger.error("Error inserting category into database: " + e.getMessage());
            // Keep in-memory consistent with DB failure by removing the category
            categories.remove(categoryId);
            return null;
        }
    }

    public boolean updateCategory(String categoryId, String newCategoryName) {
        if (!categories.containsKey(categoryId)) {
            return false;
        }

        ContractCategory oldCategory = categories.get(categoryId);

        // Create new category with updated name
        ContractCategory updatedCategory = new ContractCategory(
                categoryId,
                newCategoryName,
                oldCategory.getDisplayItem(),
                oldCategory.getCreatedAt()
        );

        // Update in map
        categories.put(categoryId, updatedCategory);

        // Update in database
        String query = "UPDATE contract_categories SET category_name = ? WHERE category_id = ?";

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

        String query = "DELETE FROM contract_categories WHERE category_id = ?";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, categoryId);
            stmt.executeUpdate();

            String updateAdminQuery = "UPDATE contracts SET category_id = NULL WHERE category_id = ?";
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

    public ContractCategory getCategoryById(String categoryId) {
        return categories.get(categoryId);
    }

    public ContractCategory getCategoryByName(String categoryName) {
        return categories.values().stream()
                .filter(cat -> cat.getCategoryName().equalsIgnoreCase(categoryName))
                .findFirst()
                .orElse(null);
    }

    public Collection<ContractCategory> getAllCategories() {
        return categories.values();
    }

    /**
     * Save all categories from memory to the database in a single transaction.
     * Intended to be run on plugin disable as a final sync/fallback.
     */
    public void saveCategories() {
        if (!databaseManager.isConnectionValid()) {
            NLogger.error("Database connection is null or invalid. Cannot save categories.");
            return;
        }

        String query;
        if (databaseManager.isUsingSQLite()) {
            query = """
                INSERT OR REPLACE INTO contract_categories (category_id, category_name, display_item, created_at)
                VALUES (?, ?, ?, ?)
                """;
        } else {
            query = """
                INSERT INTO contract_categories (category_id, category_name, display_item, created_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                category_name = VALUES(category_name),
                display_item = VALUES(display_item)
                """;
        }

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            conn.setAutoCommit(false);

            for (ContractCategory cat : categories.values()) {
                stmt.setString(1, cat.getCategoryId());
                stmt.setString(2, cat.getCategoryName());
                stmt.setString(3, cat.getDisplayItem());
                if (cat.getCreatedAt() != null) {
                    stmt.setTimestamp(4, java.sql.Timestamp.valueOf(cat.getCreatedAt()));
                } else {
                    stmt.setNull(4, java.sql.Types.TIMESTAMP);
                }
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            NLogger.error("Error saving all categories: " + e.getMessage());
            e.printStackTrace();
        }
    }
}