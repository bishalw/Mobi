package com.mobi.inventory.dto;

import com.mobi.inventory.entity.AdjustmentReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdjustStockRequest(
        @NotNull(message = "quantityDelta is required") Integer quantityDelta,
        @NotNull(message = "reason is required") AdjustmentReason reason,
        @Size(max = 500, message = "note cannot exceed 500 characters") String note) {
}
