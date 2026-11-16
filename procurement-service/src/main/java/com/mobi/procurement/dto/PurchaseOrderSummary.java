package com.mobi.procurement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mobi.procurement.entity.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A row in the PO list. {@code total} is omitted for warehouse users. {@code expectedArrival}
 * comes from the latest shipment notice, for the receiving queue.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PurchaseOrderSummary(
        Long id,
        String poNumber,
        Long supplierId,
        String supplierName,
        PurchaseOrderStatus status,
        LocalDate expectedDate,
        LocalDate expectedArrival,
        int lineCount,
        int unitsOrdered,
        int unitsOutstanding,
        BigDecimal total,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
