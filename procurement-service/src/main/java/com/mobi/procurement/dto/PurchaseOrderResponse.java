package com.mobi.procurement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mobi.procurement.entity.PurchaseOrderStatus;
import com.mobi.procurement.service.PurchaseOrderAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * One PO in full. What a caller sees depends on role (spec §12): warehouse users get no
 * costs, suppliers get no internal fields such as {@code createdBy}. Hidden fields are
 * left out of the JSON. {@code actions} lists what this caller can do next.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PurchaseOrderResponse(
        Long id,
        String poNumber,
        Long supplierId,
        String supplierName,
        PurchaseOrderStatus status,
        String statusNote,
        LocalDate expectedDate,
        String notes,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime sentAt,
        LocalDateTime acceptedAt,
        LocalDateTime rejectedAt,
        LocalDateTime cancelledAt,
        LocalDateTime closedAt,
        BigDecimal total,
        List<Line> lines,
        List<Shipment> shipments,
        List<ReceiptView> receipts,
        Set<PurchaseOrderAction> actions) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(Long id, Long productId, String supplierSku, int quantityOrdered, BigDecimal unitCost,
                       BigDecimal lineTotal, int quantityShipped, int quantityReceived, int quantityDamaged,
                       int outstanding) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Shipment(Long id, String carrier, String trackingNumber, LocalDate expectedArrival,
                           String createdBy, LocalDateTime createdAt, List<ShipmentLine> lines) {
    }

    public record ShipmentLine(Long poLineId, Long productId, int quantityShipped) {
    }

    /** {@code receivedBy} is left out for suppliers. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReceiptView(Long id, String receivedBy, LocalDateTime receivedAt, String note, List<ReceiptLineView> lines) {
    }

    public record ReceiptLineView(Long poLineId, Long productId, int quantityGood, int quantityDamaged) {
    }
}
