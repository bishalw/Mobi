package com.mobi.inventory.service;

import com.mobi.inventory.dto.PageResponse;
import com.mobi.inventory.dto.StockAdjustmentResponse;
import com.mobi.inventory.dto.StockAdjustmentResult;
import com.mobi.inventory.dto.UpdateInventoryRequest;
import com.mobi.inventory.dto.InventoryResponse;
import com.mobi.inventory.entity.AdjustmentReason;
import com.mobi.inventory.entity.InventoryItem;
import com.mobi.inventory.entity.StockAdjustment;
import com.mobi.inventory.event.LowStockAlerts;
import com.mobi.inventory.exception.BadRequestException;
import com.mobi.inventory.exception.InsufficientInventoryException;
import com.mobi.inventory.exception.ResourceNotFoundException;
import com.mobi.inventory.repository.InventoryRepository;
import com.mobi.inventory.repository.StockAdjustmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The only way stock changes outside checkout. Each change updates the stock record and
 * appends a ledger row in the same transaction, so the two can never disagree.
 */
@Service
public class StockLedgerService {

    private static final Logger log = LoggerFactory.getLogger(StockLedgerService.class);
    static final int MAX_PAGE_SIZE = 100;

    private final InventoryRepository inventoryRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final LowStockAlerts lowStockAlerts;

    public StockLedgerService(InventoryRepository inventoryRepository, StockAdjustmentRepository adjustmentRepository,
                              LowStockAlerts lowStockAlerts) {
        this.inventoryRepository = inventoryRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.lowStockAlerts = lowStockAlerts;
    }

    /** A manual adjustment by warehouse staff or an admin. RECEIVED is reserved for goods receipts. */
    @Transactional
    public StockAdjustmentResult adjustManually(Long productId, int delta, AdjustmentReason reason, String note, String actor) {
        if (reason == AdjustmentReason.RECEIVED) {
            throw new BadRequestException("RECEIVED stock is recorded by receiving a purchase order, not by hand");
        }
        return adjust(productId, delta, reason, note, null, null, actor);
    }

    /**
     * Applies one change. With a reference (e.g. {@code PO_RECEIPT}, receipt id) the change
     * happens at most once per product: a repeat returns the original result unchanged.
     */
    @Transactional
    public StockAdjustmentResult adjust(Long productId, int delta, AdjustmentReason reason, String note,
                                        String referenceType, String referenceId, String actor) {
        if (!reason.allows(delta)) {
            throw new BadRequestException(reason + " does not allow a change of " + delta);
        }
        String trimmedNote = note == null || note.isBlank() ? null : note.trim();
        if (reason.requiresNote() && trimmedNote == null) {
            throw new BadRequestException("A note is required for " + reason);
        }

        if (referenceType != null) {
            Optional<StockAdjustment> previous = adjustmentRepository
                    .findByReferenceTypeAndReferenceIdAndProductId(referenceType, referenceId, productId);
            if (previous.isPresent()) {
                log.info("Adjustment {}:{} for product {} already applied, skipping", referenceType, referenceId, productId);
                return new StockAdjustmentResult(InventoryMapper.toResponse(find(productId)), StockAdjustmentResponse.from(previous.get()));
            }
        }

        // Locked like checkout's reservations, so an adjustment and a sale of the same
        // product queue up instead of overwriting each other.
        InventoryItem item = inventoryRepository.findByProductIdInWithLock(List.of(productId)).stream().findFirst()
                .orElseGet(() -> {
                    if (delta < 0) {
                        throw new ResourceNotFoundException("No stock recorded for product id: " + productId);
                    }
                    // First stock for a product creates its record: no separate setup step.
                    return new InventoryItem(productId, 0, null);
                });

        int unsoldBefore = LowStockAlerts.unsold(item);
        int after = item.getAvailableQuantity() + delta;
        if (after < 0) {
            throw new InsufficientInventoryException("Cannot remove " + (-delta) + " from product " + productId
                    + ": only " + item.getAvailableQuantity() + " available");
        }
        item.setAvailableQuantity(after);
        InventoryItem saved = inventoryRepository.save(item);
        StockAdjustment adjustment = adjustmentRepository.save(new StockAdjustment(
                productId, delta, reason, trimmedNote, referenceType, referenceId, actor, after));
        lowStockAlerts.afterChange(saved, unsoldBefore, reason.name());
        log.info("Stock {} {} for product {} by {} (now {})", reason, delta, productId, actor, after);
        return new StockAdjustmentResult(InventoryMapper.toResponse(saved), StockAdjustmentResponse.from(adjustment));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockAdjustmentResponse> history(Long productId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        return PageResponse.from(
                adjustmentRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(page, size)),
                StockAdjustmentResponse::from);
    }

    @Transactional
    public InventoryResponse updateDetails(Long productId, UpdateInventoryRequest request) {
        InventoryItem item = find(productId);
        if (request.warehouseLocation() != null) {
            item.setWarehouseLocation(request.warehouseLocation().isBlank() ? null : request.warehouseLocation().trim());
        }
        if (request.reorderLevel() != null) {
            item.setReorderLevel(request.reorderLevel());
        }
        return InventoryMapper.toResponse(inventoryRepository.save(item));
    }

    private InventoryItem find(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("No stock recorded for product id: " + productId));
    }
}
