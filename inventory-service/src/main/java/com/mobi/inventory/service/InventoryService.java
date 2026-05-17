package com.mobi.inventory.service;

import com.mobi.inventory.dto.BatchReserveRequest;
import com.mobi.inventory.dto.BatchReserveResponse;
import com.mobi.inventory.dto.CreateInventoryRequest;
import com.mobi.inventory.dto.InventoryResponse;

import java.util.List;

public interface InventoryService {

    InventoryResponse createInventory(CreateInventoryRequest request);

    InventoryResponse getInventoryByProductId(Long productId);

    List<InventoryResponse> getAllInventory();

    InventoryResponse reserve(Long productId, int quantity);

    InventoryResponse release(Long productId, int quantity);

    InventoryResponse deduct(Long productId, int quantity);

    BatchReserveResponse batchReserve(BatchReserveRequest request);

    void releaseReservation(String reservationKey);

    void confirmReservation(String reservationKey);
}
