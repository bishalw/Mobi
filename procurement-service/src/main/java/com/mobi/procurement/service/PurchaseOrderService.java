package com.mobi.procurement.service;

import com.mobi.procurement.dto.AcceptPurchaseOrderRequest;
import com.mobi.procurement.dto.PageResponse;
import com.mobi.procurement.dto.PurchaseOrderRequest;
import com.mobi.procurement.dto.PurchaseOrderResponse;
import com.mobi.procurement.dto.PurchaseOrderSummary;
import com.mobi.procurement.dto.ReceiptRequest;
import com.mobi.procurement.dto.ShipmentNoticeRequest;
import com.mobi.procurement.dto.StatusNoteRequest;
import com.mobi.procurement.entity.PurchaseOrder;
import com.mobi.procurement.entity.PurchaseOrderLine;
import com.mobi.procurement.entity.PurchaseOrderStatus;
import com.mobi.procurement.entity.Receipt;
import com.mobi.procurement.entity.ShipmentNotice;
import com.mobi.procurement.entity.Supplier;
import com.mobi.procurement.entity.SupplierProduct;
import com.mobi.procurement.event.ProcurementEvent;
import com.mobi.procurement.event.ProcurementEventPublisher;
import com.mobi.procurement.event.ProcurementEventType;
import com.mobi.procurement.exception.BadRequestException;
import com.mobi.procurement.exception.ConflictException;
import com.mobi.procurement.exception.NotFoundException;
import com.mobi.procurement.repository.PurchaseOrderRepository;
import com.mobi.procurement.repository.ReceiptRepository;
import com.mobi.procurement.repository.ShipmentNoticeRepository;
import com.mobi.procurement.repository.SupplierProductRepository;
import com.mobi.procurement.repository.SupplierRepository;
import com.mobi.procurement.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Purchase orders: drafting and sending (admin), accepting, rejecting and shipping
 * (the PO's own supplier). Status rules live in {@link PurchaseOrderStateMachine}.
 *
 * <p>A supplier only ever sees its own POs, and never drafts. Anything else reads as
 * 404, not 403, so a supplier can't tell another supplier's PO from one that doesn't exist.
 */
@Service
public class PurchaseOrderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final ShipmentNoticeRepository shipmentNoticeRepository;
    private final ReceiptRepository receiptRepository;
    private final ProcurementEventPublisher eventPublisher;
    private final PurchaseOrderViews views;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SupplierRepository supplierRepository,
                                SupplierProductRepository supplierProductRepository,
                                ShipmentNoticeRepository shipmentNoticeRepository,
                                ReceiptRepository receiptRepository,
                                ProcurementEventPublisher eventPublisher,
                                PurchaseOrderViews views) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
        this.shipmentNoticeRepository = shipmentNoticeRepository;
        this.receiptRepository = receiptRepository;
        this.eventPublisher = eventPublisher;
        this.views = views;
    }

    // --- Reads ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderSummary> list(CurrentUser caller, Set<PurchaseOrderStatus> statuses,
                                                   Long supplierId, int page, int size) {
        Set<PurchaseOrderStatus> wanted = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(PurchaseOrderStatus.class) : EnumSet.copyOf(statuses);
        if (caller.isSupplier()) {
            if (caller.supplierId() == null) {
                return new PageResponse<>(List.of(), page, size, 0, 0);
            }
            supplierId = caller.supplierId();
            wanted.remove(PurchaseOrderStatus.DRAFT);
            if (wanted.isEmpty()) {
                return new PageResponse<>(List.of(), page, size, 0, 0);
            }
        }
        Page<PurchaseOrder> result = purchaseOrderRepository.search(wanted, supplierId, caller.isSupplier(),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
        Map<Long, String> names = supplierNames(result.getContent().stream().map(PurchaseOrder::getSupplierId).toList());
        Map<Long, LocalDate> arrivals = latestArrivals(result.getContent().stream().map(PurchaseOrder::getId).toList());
        return PageResponse.from(result.map(po -> views.summary(caller, po, names.get(po.getSupplierId()), arrivals.get(po.getId()))));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(CurrentUser caller, Long id) {
        return detail(caller, load(caller, id));
    }

    // --- Admin ---------------------------------------------------------------------

    @Transactional
    public PurchaseOrderResponse create(CurrentUser caller, PurchaseOrderRequest request) {
        Supplier supplier = activeSupplier(request.supplierId());
        PurchaseOrder po = new PurchaseOrder(supplier.getId(), request.expectedDate(), blankToNull(request.notes()),
                caller.username());
        buildLines(supplier, request.lines()).forEach(po::addLine);
        po = purchaseOrderRepository.saveAndFlush(po);
        po.assignPoNumber();
        return detail(caller, po);
    }

    @Transactional
    public PurchaseOrderResponse update(CurrentUser caller, Long id, PurchaseOrderRequest request) {
        PurchaseOrder po = load(caller, id);
        PurchaseOrderStateMachine.check(po, PurchaseOrderAction.EDIT);
        Supplier supplier = activeSupplier(request.supplierId());
        po.revise(supplier.getId(), request.expectedDate(), blankToNull(request.notes()));
        List<PurchaseOrderLine> lines = buildLines(supplier, request.lines());
        // Delete the old lines before inserting the new ones: Hibernate flushes inserts
        // first, which would trip the (po_id, product_id) unique key for a kept product.
        po.clearLines();
        purchaseOrderRepository.saveAndFlush(po);
        lines.forEach(po::addLine);
        return detail(caller, purchaseOrderRepository.saveAndFlush(po));
    }

    @Transactional
    public PurchaseOrderResponse send(CurrentUser caller, Long id) {
        PurchaseOrder po = load(caller, id);
        PurchaseOrderStateMachine.check(po, PurchaseOrderAction.SEND);
        Supplier supplier = activeSupplier(po.getSupplierId());
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.SEND, null);
        eventPublisher.publish(ProcurementEventType.PO_SENT, po, supplier, caller.username(), null);
        return detail(caller, po);
    }

    @Transactional
    public PurchaseOrderResponse cancel(CurrentUser caller, Long id, StatusNoteRequest request) {
        PurchaseOrder po = load(caller, id);
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.CANCEL, note(request));
        return detail(caller, po);
    }

    /** Close short: the rest isn't coming. Keeps the receipts already counted. */
    @Transactional
    public PurchaseOrderResponse close(CurrentUser caller, Long id, StatusNoteRequest request) {
        PurchaseOrder po = load(caller, id);
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.CLOSE, requiredNote(request, "closing a purchase order short"));
        return detail(caller, po);
    }

    // --- Supplier ------------------------------------------------------------------

    @Transactional
    public PurchaseOrderResponse accept(CurrentUser caller, Long id, AcceptPurchaseOrderRequest request) {
        PurchaseOrder po = load(caller, id);
        PurchaseOrderStateMachine.check(po, PurchaseOrderAction.ACCEPT);
        // "Today" for a supplier may be yesterday in UTC (the server's zone); the earliest
        // calendar date anywhere is UTC minus one day (UTC-12 at most).
        if (request.expectedDate().isBefore(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1))) {
            throw new BadRequestException("The delivery date can't be in the past");
        }
        po.setExpectedDate(request.expectedDate());
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.ACCEPT, null);
        eventPublisher.publish(ProcurementEventType.PO_ACCEPTED, po, supplierOf(po), caller.username(), null);
        return detail(caller, po);
    }

    @Transactional
    public PurchaseOrderResponse reject(CurrentUser caller, Long id, StatusNoteRequest request) {
        PurchaseOrder po = load(caller, id);
        String reason = requiredNote(request, "rejecting a purchase order");
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.REJECT, reason);
        eventPublisher.publish(ProcurementEventType.PO_REJECTED, po, supplierOf(po), caller.username(), reason);
        return detail(caller, po);
    }

    /**
     * Records what the supplier says it shipped. Informational only: stock moves when the
     * warehouse counts the goods in, never on a supplier's word.
     */
    @Transactional
    public PurchaseOrderResponse ship(CurrentUser caller, Long id, ShipmentNoticeRequest request) {
        PurchaseOrder po = load(caller, id);
        PurchaseOrderStateMachine.check(po, PurchaseOrderAction.SHIP);

        ShipmentNotice notice = new ShipmentNotice(po.getId(), request.carrier().trim(),
                blankToNull(request.trackingNumber()), request.expectedArrival(), caller.username());
        Set<Long> seen = new HashSet<>();
        List<ProcurementEvent.Line> eventLines = new ArrayList<>();
        for (ShipmentNoticeRequest.Line requested : request.lines()) {
            if (!seen.add(requested.poLineId())) {
                throw new BadRequestException("Line " + requested.poLineId() + " is listed twice");
            }
            PurchaseOrderLine line = po.line(requested.poLineId())
                    .orElseThrow(() -> new BadRequestException("Line " + requested.poLineId() + " is not on " + po.getPoNumber()));
            // Limited by what hasn't been counted in yet, so after a short receipt the supplier
            // can announce replacements, and can never announce more than can be received.
            if (requested.quantityShipped() > line.outstanding()) {
                throw new BadRequestException("Product %d: %d shipped would exceed the %d still outstanding (ordered %d)"
                        .formatted(line.getProductId(), requested.quantityShipped(), line.outstanding(),
                                line.getQuantityOrdered()));
            }
            line.recordShipped(requested.quantityShipped());
            notice.addLine(line.getId(), requested.quantityShipped());
            eventLines.add(new ProcurementEvent.Line(line.getProductId(), line.getQuantityOrdered(),
                    requested.quantityShipped(), null, null));
        }
        shipmentNoticeRepository.save(notice);
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.SHIP, null);
        // Bumps @Version even when the status stays put, so two notices racing on one PO
        // can't both pass the "≤ ordered" check.
        po.touch();
        purchaseOrderRepository.saveAndFlush(po);
        eventPublisher.publish(ProcurementEventType.SHIPMENT_NOTICE_CREATED, po, supplierOf(po), caller.username(), null,
                notice.getId(), null, eventLines);
        return detail(caller, po);
    }

    // --- Warehouse -----------------------------------------------------------------

    /**
     * Counts goods in, in one transaction: the receipt, the PO's running totals and status,
     * and a GOODS_RECEIVED outbox event that inventory-service turns into stock. The
     * Idempotency-Key makes a retried submit return the original result instead of
     * counting twice.
     */
    @Transactional
    public PurchaseOrderResponse receive(CurrentUser caller, Long id, String idempotencyKey, ReceiptRequest request) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 100) {
            throw new BadRequestException("Idempotency-Key header is required (at most 100 characters)");
        }
        String fingerprint = fingerprint(request);
        Optional<Receipt> previous = receiptRepository.findByReceiptKey(key);
        if (previous.isPresent()) {
            if (!previous.get().getPoId().equals(id)) {
                throw new ConflictException("Idempotency-Key " + key + " was already used for another purchase order");
            }
            if (previous.get().getRequestHash() != null && !previous.get().getRequestHash().equals(fingerprint)) {
                throw new ConflictException("Idempotency-Key " + key
                        + " was already used for a receipt with different counts; reload the purchase order");
            }
            return detail(caller, load(caller, id));
        }

        PurchaseOrder po = load(caller, id);
        PurchaseOrderStateMachine.check(po, PurchaseOrderAction.RECEIVE);

        Receipt receipt = new Receipt(key, po.getId(), caller.username(), blankToNull(request.note()), fingerprint);
        List<ProcurementEvent.Line> eventLines = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ReceiptRequest.Line counted : request.lines()) {
            if (!seen.add(counted.poLineId())) {
                throw new BadRequestException("Line " + counted.poLineId() + " is listed twice");
            }
            PurchaseOrderLine line = po.line(counted.poLineId())
                    .orElseThrow(() -> new BadRequestException("Line " + counted.poLineId() + " is not on " + po.getPoNumber()));
            int units = Math.addExact(counted.quantityGood(), counted.quantityDamaged());
            if (units == 0) {
                continue;
            }
            if (units > line.outstanding()) {
                throw new ConflictException("Product %d: counting %d would exceed the %d still outstanding (ordered %d)"
                        .formatted(line.getProductId(), units, line.outstanding(), line.getQuantityOrdered()));
            }
            line.recordReceived(counted.quantityGood(), counted.quantityDamaged());
            receipt.addLine(line.getId(), counted.quantityGood(), counted.quantityDamaged());
            eventLines.add(new ProcurementEvent.Line(line.getProductId(), null, null,
                    counted.quantityGood(), counted.quantityDamaged()));
        }
        if (receipt.getLines().isEmpty()) {
            throw new BadRequestException("Nothing was counted");
        }

        receiptRepository.saveAndFlush(receipt);
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.RECEIVE, null);
        // Bumps @Version even if the status stays PARTIALLY_RECEIVED, so two counts racing on
        // one PO can't both pass the outstanding check.
        po.touch();
        purchaseOrderRepository.saveAndFlush(po);
        eventPublisher.publish(ProcurementEventType.GOODS_RECEIVED, po, supplierOf(po), caller.username(), receipt.getNote(),
                null, receipt.getId(), eventLines);
        return detail(caller, po);
    }

    // --- Helpers -------------------------------------------------------------------

    /** The PO if this caller may see it, else 404. */
    PurchaseOrder load(CurrentUser caller, Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Purchase order " + id + " not found"));
        // Never-sent POs (drafts, and drafts cancelled before sending) are internal.
        if (caller.isSupplier() && (caller.supplierId() == null
                || !caller.supplierId().equals(po.getSupplierId())
                || po.getSentAt() == null)) {
            throw new NotFoundException("Purchase order " + id + " not found");
        }
        return po;
    }

    private PurchaseOrderResponse detail(CurrentUser caller, PurchaseOrder po) {
        String supplierName = supplierRepository.findById(po.getSupplierId()).map(Supplier::getName).orElse(null);
        Map<Long, String> skus = supplierProductRepository.findBySupplierIdOrderByProductIdAsc(po.getSupplierId()).stream()
                .filter(product -> product.getSupplierSku() != null)
                .collect(Collectors.toMap(SupplierProduct::getProductId, SupplierProduct::getSupplierSku));
        List<ShipmentNotice> notices = shipmentNoticeRepository.findByPoIdOrderByCreatedAtAscIdAsc(po.getId());
        List<Receipt> receipts = receiptRepository.findByPoIdOrderByReceivedAtAscIdAsc(po.getId());
        return views.detail(caller, po, supplierName, skus, notices, receipts);
    }

    /** Order-independent fingerprint of what was counted (lines with nothing counted ignored). */
    static String fingerprint(ReceiptRequest request) {
        String canonical = request.lines().stream()
                .filter(l -> l.quantityGood() + l.quantityDamaged() > 0)
                .sorted(java.util.Comparator.comparing(ReceiptRequest.Line::poLineId))
                .map(l -> l.poLineId() + ":" + l.quantityGood() + ":" + l.quantityDamaged())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exp) {
            throw new IllegalStateException(exp);
        }
    }

    private Supplier supplierOf(PurchaseOrder po) {
        return supplierRepository.findById(po.getSupplierId()).orElse(null);
    }

    private Supplier activeSupplier(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new BadRequestException("Supplier " + supplierId + " not found"));
        if (!supplier.isActive()) {
            throw new BadRequestException("Supplier " + supplier.getName() + " is inactive");
        }
        return supplier;
    }

    /** Every product must be in the supplier's catalogue; its price is the default unit cost. */
    private List<PurchaseOrderLine> buildLines(Supplier supplier, List<PurchaseOrderRequest.Line> requested) {
        Map<Long, SupplierProduct> catalogue = supplierProductRepository.findBySupplierIdOrderByProductIdAsc(supplier.getId())
                .stream().collect(Collectors.toMap(SupplierProduct::getProductId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        List<PurchaseOrderLine> lines = new ArrayList<>();
        for (PurchaseOrderRequest.Line line : requested) {
            if (!seen.add(line.productId())) {
                throw new BadRequestException("Product " + line.productId() + " is listed twice");
            }
            SupplierProduct offered = catalogue.get(line.productId());
            if (offered == null) {
                throw new BadRequestException("Product " + line.productId() + " is not in "
                        + supplier.getName() + "'s catalogue");
            }
            lines.add(new PurchaseOrderLine(line.productId(), line.quantity(),
                    line.unitCost() != null ? line.unitCost() : offered.getUnitCost()));
        }
        return lines;
    }

    /** Per PO, the expected arrival on its most recent shipment notice that gave one. */
    private Map<Long, LocalDate> latestArrivals(Collection<Long> poIds) {
        Map<Long, ShipmentNotice> latest = new HashMap<>();
        if (poIds.isEmpty()) {
            return Map.of();
        }
        for (ShipmentNotice notice : shipmentNoticeRepository.findByPoIdIn(poIds)) {
            if (notice.getExpectedArrival() == null) {
                continue;
            }
            latest.merge(notice.getPoId(), notice, (a, b) -> b.getId() > a.getId() ? b : a);
        }
        Map<Long, LocalDate> arrivals = new HashMap<>();
        latest.forEach((poId, notice) -> arrivals.put(poId, notice.getExpectedArrival()));
        return arrivals;
    }

    private Map<Long, String> supplierNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        supplierRepository.findAllById(new HashSet<>(ids)).forEach(s -> names.put(s.getId(), s.getName()));
        return names;
    }

    private static String note(StatusNoteRequest request) {
        return request == null ? null : blankToNull(request.note());
    }

    private static String requiredNote(StatusNoteRequest request, String what) {
        String note = note(request);
        if (note == null) {
            throw new BadRequestException("A note is required when " + what);
        }
        return note;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
