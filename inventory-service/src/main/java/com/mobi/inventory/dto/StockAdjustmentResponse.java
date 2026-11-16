package com.mobi.inventory.dto;

import com.mobi.inventory.entity.AdjustmentReason;
import com.mobi.inventory.entity.StockAdjustment;

import java.time.LocalDateTime;

public record StockAdjustmentResponse(Long id, Long productId, int quantityDelta, AdjustmentReason reason, String note,
                                      String referenceType, String referenceId, String createdBy,
                                      LocalDateTime createdAt, int availableAfter) {

    public static StockAdjustmentResponse from(StockAdjustment a) {
        return new StockAdjustmentResponse(a.getId(), a.getProductId(), a.getQuantityDelta(), a.getReason(), a.getNote(),
                a.getReferenceType(), a.getReferenceId(), a.getCreatedBy(), a.getCreatedAt(), a.getAvailableAfter());
    }
}
