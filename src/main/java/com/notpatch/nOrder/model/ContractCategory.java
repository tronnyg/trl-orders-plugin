package com.notpatch.nOrder.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ContractCategory {

    private final String categoryId;
    private final String categoryName;
    private final String displayItem; // Material name for GUI display
    private final LocalDateTime createdAt;

    public ContractCategory(String categoryId, String categoryName, String displayItem) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.displayItem = displayItem;
        this.createdAt = LocalDateTime.now();
    }

    public ContractCategory(String categoryId, String categoryName, String displayItem, LocalDateTime createdAt) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.displayItem = displayItem;
        this.createdAt = createdAt;
    }
}