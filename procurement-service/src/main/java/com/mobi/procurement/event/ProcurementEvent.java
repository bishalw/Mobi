package com.mobi.procurement.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * One message on {@code procurement-events}, keyed by PO id so a PO's events stay in
 * order. Fields a type doesn't use are left out of the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcurementEvent(
        String eventId,
        ProcurementEventType type,
        Instant occurredAt,
        Long poId,
        String poNumber,
        Long supplierId,
        String supplierName,
        String supplierEmail,
        String status,
        String actor,
        String note,
        Long shipmentNoticeId,
        Long receiptId,
        List<Line> lines) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(Long productId, Integer quantityOrdered, Integer quantityShipped,
                       Integer quantityGood, Integer quantityDamaged) {
    }
}
