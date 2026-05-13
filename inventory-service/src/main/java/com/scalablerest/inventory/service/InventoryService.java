package com.scalablerest.inventory.service;

import com.scalablerest.inventory.dto.CreateInventoryRequest;
import com.scalablerest.inventory.dto.InventoryResponse;

import java.util.List;

public interface InventoryService {

    InventoryResponse createInventory(CreateInventoryRequest request);

    InventoryResponse getInventoryByProductId(Long productId);

    List<InventoryResponse> getAllInventory();

    InventoryResponse reserve(Long productId, int quantity);

    InventoryResponse release(Long productId, int quantity);

    InventoryResponse deduct(Long productId, int quantity);
}
