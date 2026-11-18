package com.mobi.inventory.service;

import com.mobi.inventory.dto.BatchReserveRequest;
import com.mobi.inventory.dto.BatchReserveResponse;
import com.mobi.inventory.dto.InventoryResponse;

import java.util.List;

public interface InventoryService {

    InventoryResponse getInventoryByProductId(Long productId);

    List<InventoryResponse> getAllInventory();

    BatchReserveResponse batchReserve(BatchReserveRequest request);

    void releaseReservation(String reservationKey);

    void confirmReservation(String reservationKey);

    void returnReservation(String reservationKey);
}
