package com.scalablerest.inventory.service;

import com.scalablerest.inventory.dto.BatchReserveItemRequest;
import com.scalablerest.inventory.dto.BatchReserveRequest;
import com.scalablerest.inventory.dto.BatchReserveResponse;
import com.scalablerest.inventory.dto.CreateInventoryRequest;
import com.scalablerest.inventory.dto.InventoryResponse;
import com.scalablerest.inventory.entity.InventoryItem;
import com.scalablerest.inventory.entity.Reservation;
import com.scalablerest.inventory.entity.ReservationItem;
import com.scalablerest.inventory.entity.ReservationStatus;
import com.scalablerest.inventory.exception.InsufficientInventoryException;
import com.scalablerest.inventory.exception.ResourceNotFoundException;
import com.scalablerest.inventory.repository.InventoryRepository;
import com.scalablerest.inventory.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository, ReservationRepository reservationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
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

    @Override
    @Transactional
    public BatchReserveResponse batchReserve(BatchReserveRequest request) {
        return reservationRepository.findByReservationKey(request.getReservationKey())
                .filter(r -> r.getStatus() == ReservationStatus.ACTIVE)
                .map(this::mapToReservationResponse)
                .orElseGet(() -> doReserve(request));
    }

    private BatchReserveResponse doReserve(BatchReserveRequest request) {
        List<Long> productIds = request.getItems().stream()
                .map(BatchReserveItemRequest::getProductId)
                .toList();

        Map<Long, InventoryItem> itemsByProductId = inventoryRepository.findByProductIdInWithLock(productIds)
                .stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));

        for (BatchReserveItemRequest line : request.getItems()) {
            InventoryItem item = itemsByProductId.get(line.getProductId());
            if (item == null) {
                throw new ResourceNotFoundException("Inventory not found for product id: " + line.getProductId());
            }
            if (item.getAvailableQuantity() < line.getQuantity()) {
                throw new InsufficientInventoryException(
                        "Insufficient inventory for product id: " + line.getProductId()
                        + " (requested=" + line.getQuantity()
                        + ", available=" + item.getAvailableQuantity() + ")");
            }
        }

        for (BatchReserveItemRequest line : request.getItems()) {
            InventoryItem item = itemsByProductId.get(line.getProductId());
            item.setAvailableQuantity(item.getAvailableQuantity() - line.getQuantity());
            item.setReservedQuantity(item.getReservedQuantity() + line.getQuantity());
            inventoryRepository.save(item);
        }

        Reservation reservation = new Reservation(request.getReservationKey());
        for (BatchReserveItemRequest line : request.getItems()) {
            reservation.addItem(new ReservationItem(reservation, line.getProductId(), line.getQuantity()));
        }
        return mapToReservationResponse(reservationRepository.save(reservation));
    }

    @Override
    @Transactional
    public void releaseReservation(String reservationKey) {
        Reservation reservation = reservationRepository.findByReservationKey(reservationKey)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationKey));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            return;
        }

        for (ReservationItem line : reservation.getItems()) {
            inventoryRepository.findByProductId(line.getProductId()).ifPresent(item -> {
                int qty = Math.min(line.getQuantity(), item.getReservedQuantity());
                item.setReservedQuantity(item.getReservedQuantity() - qty);
                item.setAvailableQuantity(item.getAvailableQuantity() + qty);
                inventoryRepository.save(item);
            });
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setReleasedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        log.info("Released reservation {} ({} items)", reservationKey, reservation.getItems().size());
    }

    private InventoryItem findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product id: " + productId));
    }

    private BatchReserveResponse mapToReservationResponse(Reservation reservation) {
        BatchReserveResponse response = new BatchReserveResponse();
        response.setReservationKey(reservation.getReservationKey());
        response.setStatus(reservation.getStatus().name());
        response.setReservedAt(reservation.getReservedAt());
        response.setItems(reservation.getItems().stream().map(item -> {
            BatchReserveItemRequest r = new BatchReserveItemRequest();
            r.setProductId(item.getProductId());
            r.setQuantity(item.getQuantity());
            return r;
        }).toList());
        return response;
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
