package com.scalablerest.inventory.service;

import com.scalablerest.inventory.dto.CreateInventoryRequest;
import com.scalablerest.inventory.dto.InventoryResponse;
import com.scalablerest.inventory.entity.InventoryItem;
import com.scalablerest.inventory.exception.InsufficientInventoryException;
import com.scalablerest.inventory.exception.ResourceNotFoundException;
import com.scalablerest.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public InventoryResponse createInventory(CreateInventoryRequest request) {
        InventoryItem item = new InventoryItem(request.getProductId(), request.getAvailableQuantity(), request.getWarehouseLocation());
        return mapToResponse(inventoryRepository.save(item));
    }

    @Override
    public InventoryResponse getInventoryByProductId(Long productId) {
        return mapToResponse(findByProductId(productId));
    }

    @Override
    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public InventoryResponse reserve(Long productId, int quantity) {
        InventoryItem item = findByProductId(productId);
        if (item.getAvailableQuantity() < quantity) {
            throw new InsufficientInventoryException("Not enough inventory available for product id: " + productId);
        }
        item.setAvailableQuantity(item.getAvailableQuantity() - quantity);
        item.setReservedQuantity(item.getReservedQuantity() + quantity);
        return mapToResponse(inventoryRepository.save(item));
    }

    @Override
    @Transactional
    public InventoryResponse release(Long productId, int quantity) {
        InventoryItem item = findByProductId(productId);
        if (item.getReservedQuantity() < quantity) {
            throw new InsufficientInventoryException("Cannot release more inventory than is reserved for product id: " + productId);
        }
        item.setReservedQuantity(item.getReservedQuantity() - quantity);
        item.setAvailableQuantity(item.getAvailableQuantity() + quantity);
        return mapToResponse(inventoryRepository.save(item));
    }

    @Override
    @Transactional
    public InventoryResponse deduct(Long productId, int quantity) {
        InventoryItem item = findByProductId(productId);
        if (item.getReservedQuantity() < quantity) {
            throw new InsufficientInventoryException("Cannot deduct more inventory than is reserved for product id: " + productId);
        }
        item.setReservedQuantity(item.getReservedQuantity() - quantity);
        return mapToResponse(inventoryRepository.save(item));
    }

    private InventoryItem findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
    }

    private InventoryResponse mapToResponse(InventoryItem item) {
        InventoryResponse response = new InventoryResponse();
        response.setId(item.getId());
        response.setProductId(item.getProductId());
        response.setAvailableQuantity(item.getAvailableQuantity());
        response.setReservedQuantity(item.getReservedQuantity());
        response.setWarehouseLocation(item.getWarehouseLocation());
        response.setVersion(item.getVersion());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        return response;
    }
}
