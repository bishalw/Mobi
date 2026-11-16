package com.mobi.procurement.service;

import com.mobi.procurement.dto.PurchaseOrderResponse;
import com.mobi.procurement.dto.PurchaseOrderSummary;
import com.mobi.procurement.entity.PurchaseOrder;
import com.mobi.procurement.entity.PurchaseOrderLine;
import com.mobi.procurement.entity.Receipt;
import com.mobi.procurement.entity.ShipmentNotice;
import com.mobi.procurement.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shapes a PO for whoever is asking (spec §12): costs only for admins and the PO's
 * supplier, internal fields such as {@code createdBy} never for suppliers, and the
 * actions each role can take from the current status.
 */
@Component
public class PurchaseOrderViews {

    private static final Set<PurchaseOrderAction> ADMIN_ACTIONS = EnumSet.of(PurchaseOrderAction.EDIT,
            PurchaseOrderAction.SEND, PurchaseOrderAction.CANCEL, PurchaseOrderAction.CLOSE, PurchaseOrderAction.RECEIVE);
    private static final Set<PurchaseOrderAction> WAREHOUSE_ACTIONS = EnumSet.of(PurchaseOrderAction.RECEIVE);
    private static final Set<PurchaseOrderAction> SUPPLIER_ACTIONS = EnumSet.of(PurchaseOrderAction.ACCEPT,
            PurchaseOrderAction.REJECT, PurchaseOrderAction.SHIP);

    public PurchaseOrderSummary summary(CurrentUser caller, PurchaseOrder po, String supplierName, LocalDate expectedArrival) {
        int ordered = po.getLines().stream().mapToInt(PurchaseOrderLine::getQuantityOrdered).sum();
        int outstanding = po.getLines().stream().mapToInt(PurchaseOrderLine::outstanding).sum();
        return new PurchaseOrderSummary(po.getId(), po.getPoNumber(), po.getSupplierId(), supplierName, po.getStatus(),
                po.getExpectedDate(), expectedArrival, po.getLines().size(), ordered, outstanding,
                showCosts(caller) ? po.total() : null, po.getCreatedAt(), po.getUpdatedAt());
    }

    public PurchaseOrderResponse detail(CurrentUser caller, PurchaseOrder po, String supplierName,
                                        Map<Long, String> skus, List<ShipmentNotice> notices, List<Receipt> receipts) {
        boolean costs = showCosts(caller);
        boolean internal = !caller.isSupplier();
        Map<Long, PurchaseOrderLine> linesById = po.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));

        List<PurchaseOrderResponse.Line> lines = po.getLines().stream()
                .map(line -> new PurchaseOrderResponse.Line(line.getId(), line.getProductId(), skus.get(line.getProductId()),
                        line.getQuantityOrdered(), costs ? line.getUnitCost() : null, costs ? line.lineTotal() : null,
                        line.getQuantityShipped(), line.getQuantityReceived(), line.getQuantityDamaged(), line.outstanding()))
                .toList();
        List<PurchaseOrderResponse.Shipment> shipments = notices.stream()
                .map(notice -> new PurchaseOrderResponse.Shipment(notice.getId(), notice.getCarrier(),
                        notice.getTrackingNumber(), notice.getExpectedArrival(), internal ? notice.getCreatedBy() : null,
                        notice.getCreatedAt(), notice.getLines().stream()
                        .map(l -> new PurchaseOrderResponse.ShipmentLine(l.getPoLineId(),
                                linesById.containsKey(l.getPoLineId()) ? linesById.get(l.getPoLineId()).getProductId() : null,
                                l.getQuantityShipped()))
                        .toList()))
                .toList();
        List<PurchaseOrderResponse.ReceiptView> receiptViews = receipts.stream()
                .map(receipt -> new PurchaseOrderResponse.ReceiptView(receipt.getId(),
                        internal ? receipt.getReceivedBy() : null, receipt.getReceivedAt(), receipt.getNote(),
                        receipt.getLines().stream()
                                .map(l -> new PurchaseOrderResponse.ReceiptLineView(l.getPoLineId(),
                                        linesById.containsKey(l.getPoLineId()) ? linesById.get(l.getPoLineId()).getProductId() : null,
                                        l.getQuantityGood(), l.getQuantityDamaged()))
                                .toList()))
                .toList();

        return new PurchaseOrderResponse(po.getId(), po.getPoNumber(), po.getSupplierId(), supplierName, po.getStatus(),
                po.getStatusNote(), po.getExpectedDate(), po.getNotes(), internal ? po.getCreatedBy() : null,
                po.getCreatedAt(), po.getUpdatedAt(), po.getSentAt(), po.getAcceptedAt(), po.getRejectedAt(),
                po.getCancelledAt(), po.getClosedAt(), costs ? po.total() : null, lines, shipments, receiptViews, actions(caller, po));
    }

    Set<PurchaseOrderAction> actions(CurrentUser caller, PurchaseOrder po) {
        Set<PurchaseOrderAction> actions = PurchaseOrderStateMachine.available(po.getStatus());
        actions.retainAll(caller.isAdmin() ? ADMIN_ACTIONS
                : caller.isWarehouse() ? WAREHOUSE_ACTIONS
                : caller.isSupplier() ? SUPPLIER_ACTIONS
                : EnumSet.noneOf(PurchaseOrderAction.class));
        return actions;
    }

    private static boolean showCosts(CurrentUser caller) {
        return caller.isAdmin() || caller.isSupplier();
    }
}
