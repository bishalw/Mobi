package com.mobi.inventory.controller;

import com.mobi.inventory.dto.BatchReserveRequest;
import com.mobi.inventory.dto.BatchReserveResponse;
import com.mobi.inventory.dto.AdjustStockRequest;
import com.mobi.inventory.dto.InventoryResponse;
import com.mobi.inventory.dto.PageResponse;
import com.mobi.inventory.dto.StockAdjustmentResponse;
import com.mobi.inventory.dto.StockAdjustmentResult;
import com.mobi.inventory.dto.UpdateInventoryRequest;
import com.mobi.inventory.service.InventoryService;
import com.mobi.inventory.service.StockLedgerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inventoryapi")
public class InventoryController {

    private final InventoryService inventoryService;
    private final StockLedgerService stockLedgerService;

    public InventoryController(InventoryService inventoryService, StockLedgerService stockLedgerService) {
        this.inventoryService = inventoryService;
        this.stockLedgerService = stockLedgerService;
    }

    @GetMapping("/products")
    public List<InventoryResponse> getAllInventory() {
        return inventoryService.getAllInventory();
    }

    @GetMapping("/products/{productId}")
    public InventoryResponse getInventory(@PathVariable Long productId) {
        return inventoryService.getInventoryByProductId(productId);
    }

    // Warehouse and admin: the only way to change stock by hand. Creates the stock
    // record on the first positive adjustment.
    @PostMapping("/products/{productId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public StockAdjustmentResult adjust(@PathVariable Long productId, @Valid @RequestBody AdjustStockRequest request,
                                        @AuthenticationPrincipal Jwt caller) {
        return stockLedgerService.adjustManually(productId, request.quantityDelta(), request.reason(), request.note(),
                caller.getSubject());
    }

    @GetMapping("/products/{productId}/adjustments")
    public PageResponse<StockAdjustmentResponse> adjustments(@PathVariable Long productId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return stockLedgerService.history(productId, page, size);
    }

    @PatchMapping("/products/{productId}")
    public InventoryResponse updateDetails(@PathVariable Long productId, @Valid @RequestBody UpdateInventoryRequest request) {
        return stockLedgerService.updateDetails(productId, request);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchReserveResponse batchReserve(@Valid @RequestBody BatchReserveRequest request) {
        return inventoryService.batchReserve(request);
    }

    @PostMapping("/reservations/{reservationKey}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseReservation(@PathVariable String reservationKey) {
        inventoryService.releaseReservation(reservationKey);
    }

    // After a refund with restock: puts the sold quantities back on the shelf. Idempotent.
    @PostMapping("/reservations/{reservationKey}/return")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void returnReservation(@PathVariable String reservationKey) {
        inventoryService.returnReservation(reservationKey);
    }

    @PostMapping("/reservations/{reservationKey}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReservation(@PathVariable String reservationKey) {
        inventoryService.confirmReservation(reservationKey);
    }
}
