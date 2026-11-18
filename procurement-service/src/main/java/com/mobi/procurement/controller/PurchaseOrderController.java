package com.mobi.procurement.controller;

import com.mobi.procurement.dto.AcceptPurchaseOrderRequest;
import com.mobi.procurement.dto.PageResponse;
import com.mobi.procurement.dto.PurchaseOrderRequest;
import com.mobi.procurement.dto.PurchaseOrderResponse;
import com.mobi.procurement.dto.PurchaseOrderSummary;
import com.mobi.procurement.dto.ReceiptRequest;
import com.mobi.procurement.dto.ShipmentNoticeRequest;
import com.mobi.procurement.dto.StatusNoteRequest;
import com.mobi.procurement.entity.PurchaseOrderStatus;
import com.mobi.procurement.security.CurrentUser;
import com.mobi.procurement.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Role per endpoint is enforced in SecurityConfig; which POs a supplier may touch is
 * enforced in PurchaseOrderService.
 */
@RestController
@RequestMapping("/procurementapi/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    /** {@code status} may repeat or be comma-separated. Suppliers always get their own, never drafts. */
    @GetMapping
    public PageResponse<PurchaseOrderSummary> list(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) Set<PurchaseOrderStatus> status,
                                                   @RequestParam(required = false) Long supplierId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return purchaseOrderService.list(CurrentUser.from(jwt), status, supplierId, page, size);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return purchaseOrderService.get(CurrentUser.from(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.create(CurrentUser.from(jwt), request);
    }

    @PutMapping("/{id}")
    public PurchaseOrderResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                        @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.update(CurrentUser.from(jwt), id, request);
    }

    @PostMapping("/{id}/send")
    public PurchaseOrderResponse send(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return purchaseOrderService.send(CurrentUser.from(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                        @Valid @RequestBody(required = false) StatusNoteRequest request) {
        return purchaseOrderService.cancel(CurrentUser.from(jwt), id, request);
    }

    @PostMapping("/{id}/close")
    public PurchaseOrderResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                       @Valid @RequestBody StatusNoteRequest request) {
        return purchaseOrderService.close(CurrentUser.from(jwt), id, request);
    }

    @PostMapping("/{id}/accept")
    public PurchaseOrderResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                        @Valid @RequestBody AcceptPurchaseOrderRequest request) {
        return purchaseOrderService.accept(CurrentUser.from(jwt), id, request);
    }

    @PostMapping("/{id}/reject")
    public PurchaseOrderResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                        @Valid @RequestBody StatusNoteRequest request) {
        return purchaseOrderService.reject(CurrentUser.from(jwt), id, request);
    }

    /** Warehouse or admin counts goods in. Retrying with the same Idempotency-Key is safe. */
    @PostMapping("/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse receive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         @Valid @RequestBody ReceiptRequest request) {
        CurrentUser caller = CurrentUser.from(jwt);
        try {
            return purchaseOrderService.receive(caller, id, idempotencyKey, request);
        } catch (DataIntegrityViolationException raced) {
            // Two submits with one key at the same moment: both missed the lookup, and the
            // unique receipt_key let only one commit. Its transaction is done, so a fresh one
            // finds that receipt and answers the loser with the same result.
            return purchaseOrderService.receive(caller, id, idempotencyKey, request);
        }
    }

    @PostMapping("/{id}/shipments")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse ship(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                      @Valid @RequestBody ShipmentNoticeRequest request) {
        return purchaseOrderService.ship(CurrentUser.from(jwt), id, request);
    }
}
