package com.mobi.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Consumer-side copy of procurement-service's event: only what the emails need. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcurementEvent(
        String eventId,
        String type,
        String occurredAt,
        Long poId,
        String poNumber,
        Long supplierId,
        String supplierName,
        String supplierEmail,
        String status,
        String actor,
        String note,
        List<Line> lines) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(Long productId, Integer quantityOrdered, Integer quantityShipped,
                       Integer quantityGood, Integer quantityDamaged) {
    }
}
