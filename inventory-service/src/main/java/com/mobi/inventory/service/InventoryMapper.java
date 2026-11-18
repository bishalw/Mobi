package com.mobi.inventory.service;

import com.mobi.inventory.dto.InventoryResponse;
import com.mobi.inventory.entity.InventoryItem;

final class InventoryMapper {

    private InventoryMapper() {
    }

    static InventoryResponse toResponse(InventoryItem item) {
        InventoryResponse response = new InventoryResponse();
        response.setId(item.getId());
        response.setProductId(item.getProductId());
        response.setAvailableQuantity(item.getAvailableQuantity());
        response.setReservedQuantity(item.getReservedQuantity());
        response.setWarehouseLocation(item.getWarehouseLocation());
        response.setReorderLevel(item.getReorderLevel());
        response.setLowStock(item.isLowStock());
        response.setVersion(item.getVersion());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        return response;
    }
}
