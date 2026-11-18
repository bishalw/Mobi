package com.mobi.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One ledger row. Append-only: rows are never updated or deleted. */
@Entity
@Table(name = "stock_adjustments")
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantityDelta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdjustmentReason reason;

    @Column(length = 500)
    private String note;

    @Column(length = 40)
    private String referenceType;

    @Column(length = 100)
    private String referenceId;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Available stock right after this change: lets the history be read without replaying it.
    @Column(nullable = false)
    private int availableAfter;

    protected StockAdjustment() {
    }

    public StockAdjustment(Long productId, int quantityDelta, AdjustmentReason reason, String note,
                           String referenceType, String referenceId, String createdBy, int availableAfter) {
        this.productId = productId;
        this.quantityDelta = quantityDelta;
        this.reason = reason;
        this.note = note;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdBy = createdBy;
        this.availableAfter = availableAfter;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getQuantityDelta() { return quantityDelta; }
    public AdjustmentReason getReason() { return reason; }
    public String getNote() { return note; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getAvailableAfter() { return availableAfter; }
}
