package com.notpatch.nOrder.manager;

import com.notpatch.nOrder.NOrder;
import com.notpatch.nOrder.model.Order;
import com.notpatch.nOrder.util.StringUtil;
import com.notpatch.nlib.util.NLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OrderLogger {

    private final NOrder main;
    private final Path logDirectory;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public OrderLogger(NOrder main) {
        this.main = main;
        this.logDirectory = main.getDataFolder().toPath().resolve("logs");
        createLogDirectory();
    }

    private void createLogDirectory() {
        try {
            if (!Files.exists(logDirectory)) {
                Files.createDirectories(logDirectory);
            }
        } catch (IOException e) {
            NLogger.error("Failed to create log directory: " + e.getMessage());
        }
    }

    private Path getLogFile() {
        String fileName = "orders-" + LocalDateTime.now().format(dateFormatter) + ".log";
        return logDirectory.resolve(fileName);
    }

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String logLine = "[" + timestamp + "] " + message;

        try {
            Path logFile = getLogFile();
            Files.writeString(logFile, logLine + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            NLogger.error("Failed to write to log file: " + e.getMessage());
        }
    }

    public void logOrderCreated(Order order, double totalPrice) {
        String message = String.format(
                "[ORDER_CREATED] Player: %s | Order ID: %s | Item: %s | Amount: %d | Price Per Item: %.2f | Total Price: %.2f | Highlight: %s | Expires: %s",
                order.getPlayerName(),
                order.getId(),
                StringUtil.formatMaterialName(order.getMaterial()),
                order.getAmount(),
                order.getPrice(),
                totalPrice,
                order.isHighlight() ? "Yes" : "No",
                order.getExpirationDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        log(message);
    }

    public void logOrderDelivery(com.notpatch.nOrder.model.BaseOrder order, String delivererName, int deliveredAmount, double earnedMoney) {
        String message = String.format(
                "[ORDER_DELIVERY] Deliverer: %s | Order Owner: %s | Order ID: %s | Item: %s | Delivered: %d | Earned: %.2f | Progress: %d/%d",
                delivererName,
                order.getDisplayName(),
                order.getId(),
                StringUtil.formatMaterialName(order.getMaterial()),
                deliveredAmount,
                earnedMoney,
                order.getDelivered(),
                order.getAmount()
        );
        log(message);
    }

    public void logItemCollection(Order order, int collectedAmount) {
        String message = String.format(
                "[ITEM_COLLECTED] Player: %s | Order ID: %s | Item: %s | Collected: %d | Total Collected: %d/%d",
                order.getPlayerName(),
                order.getId(),
                StringUtil.formatMaterialName(order.getMaterial()),
                collectedAmount,
                order.getCollected(),
                order.getDelivered()
        );
        log(message);
    }

    public void logOrderCancelled(Order order, double refundAmount) {
        String message = String.format(
                "[ORDER_CANCELLED] Player: %s | Order ID: %s | Item: %s | Remaining: %d | Refund: %.2f",
                order.getPlayerName(),
                order.getId(),
                StringUtil.formatMaterialName(order.getMaterial()),
                order.getAmount() - order.getDelivered(),
                refundAmount
        );
        log(message);
    }

    public void logOrderCompleted(com.notpatch.nOrder.model.BaseOrder order) {
        String message = String.format(
                "[ORDER_COMPLETED] Player: %s | Order ID: %s | Item: %s | Total Delivered: %d | Total Collected: %d",
                order.getDisplayName(),
                order.getId(),
                StringUtil.formatMaterialName(order.getMaterial()),
                order.getDelivered(),
                order.getCollected()
        );
        log(message);
    }

    public void logOrderExpired(Order order, double refundAmount) {
        String message = String.format(
                "[ORDER_EXPIRED] Player: %s | Order ID: %s | Item: %s | Delivered: %d/%d | Refund: %.2f",
                order.getPlayerName(),
                order.getId(),
                StringUtil.formatMaterialName(order.getMaterial()),
                order.getDelivered(),
                order.getAmount(),
                refundAmount
        );
        log(message);
    }

    public void logAdminAction(String adminName, String action, Order order) {
        String message = String.format(
                "[ADMIN_ACTION] Admin: %s | Action: %s | Order ID: %s | Order Owner: %s | Item: %s",
                adminName,
                action,
                order.getId(),
                order.getPlayerName(),
                StringUtil.formatMaterialName(order.getMaterial())
        );
        log(message);
    }

    public void logAdminAction(String adminName, String action, String details) {
        String message = String.format(
                "[ADMIN_ACTION] Admin: %s | Action: %s | Details: %s",
                adminName,
                action,
                details
        );
        log(message);
    }

    public void logOrderArchived(Order order) {
        String message = String.format(
                "[ORDER_ARCHIVED] Player: %s | Order ID: %s | Item: %s | Total Delivered: %d | Total Collected: %d",
                order.getPlayerName(),
                order.getId(),
                StringUtil.formatMaterialName(order.getMaterial()),
                order.getDelivered(),
                order.getCollected()
        );
        log(message);
    }
}

