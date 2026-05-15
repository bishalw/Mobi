package com.scalablerest.inventory.controller;

import com.scalablerest.inventory.dto.BatchReserveRequest;
import com.scalablerest.inventory.dto.BatchReserveResponse;
import com.scalablerest.inventory.dto.CreateInventoryRequest;
import com.scalablerest.inventory.dto.InventoryQuantityRequest;
import com.scalablerest.inventory.dto.InventoryResponse;
import com.scalablerest.inventory.service.InventoryService;
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

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/products")
    public InventoryResponse createInventory(@Valid @RequestBody CreateInventoryRequest request) {
        return inventoryService.createInventory(request);
    }

    @GetMapping("/products")
    public List<InventoryResponse> getAllInventory() {
        return inventoryService.getAllInventory();
    }

    @GetMapping("/products/{productId}")
    public InventoryResponse getInventory(@PathVariable Long productId) {
        return inventoryService.getInventoryByProductId(productId);
    }

    @PostMapping("/products/{productId}/reserve")
    public InventoryResponse reserve(@PathVariable Long productId, @Valid @RequestBody InventoryQuantityRequest request) {
        return inventoryService.reserve(productId, request.getQuantity());
    }

    @PostMapping("/products/{productId}/release")
    public InventoryResponse release(@PathVariable Long productId, @Valid @RequestBody InventoryQuantityRequest request) {
        return inventoryService.release(productId, request.getQuantity());
    }

    @PostMapping("/products/{productId}/deduct")
    public InventoryResponse deduct(@PathVariable Long productId, @Valid @RequestBody InventoryQuantityRequest request) {
        return inventoryService.deduct(productId, request.getQuantity());
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
}
