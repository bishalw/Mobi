package com.mobi.procurement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One product on a PO, with running totals: shipped (what the supplier says it sent),
 * received and damaged (what the warehouse counted). Only the counts move stock.
 */
@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantityOrdered;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private int quantityShipped;

    @Column(nullable = false)
    private int quantityReceived;

    @Column(nullable = false)
    private int quantityDamaged;

    protected PurchaseOrderLine() {
    }

    public PurchaseOrderLine(Long productId, int quantityOrdered, BigDecimal unitCost) {
        this.productId = productId;
        this.quantityOrdered = quantityOrdered;
        this.unitCost = unitCost;
    }

    void attachTo(PurchaseOrder purchaseOrder) {
        this.purchaseOrder = purchaseOrder;
    }

    /** Units still to be counted in: ordered minus everything counted, good or damaged. */
    public int outstanding() {
        return Math.max(0, quantityOrdered - quantityReceived - quantityDamaged);
    }


    public BigDecimal lineTotal() {
        return unitCost.multiply(BigDecimal.valueOf(quantityOrdered));
    }

    public void recordShipped(int quantity) {
        quantityShipped += quantity;
    }

    public void recordReceived(int good, int damaged) {
        quantityReceived += good;
        quantityDamaged += damaged;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantityOrdered() {
        return quantityOrdered;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public int getQuantityShipped() {
        return quantityShipped;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public int getQuantityDamaged() {
        return quantityDamaged;
    }
}
