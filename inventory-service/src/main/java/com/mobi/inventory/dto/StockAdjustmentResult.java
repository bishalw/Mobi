package com.mobi.inventory.dto;

/** The stock record after an adjustment, and the ledger row that explains it. */
public record StockAdjustmentResult(InventoryResponse item, StockAdjustmentResponse adjustment) {
}
