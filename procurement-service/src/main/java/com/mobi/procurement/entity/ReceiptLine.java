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

@Entity
@Table(name = "receipt_lines")
public class ReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(name = "po_line_id", nullable = false)
    private Long poLineId;

    @Column(nullable = false)
    private int quantityGood;

    @Column(nullable = false)
    private int quantityDamaged;

    protected ReceiptLine() {
    }

    ReceiptLine(Receipt receipt, Long poLineId, int quantityGood, int quantityDamaged) {
        this.receipt = receipt;
        this.poLineId = poLineId;
        this.quantityGood = quantityGood;
        this.quantityDamaged = quantityDamaged;
    }

    public Long getId() {
        return id;
    }

    public Long getPoLineId() {
        return poLineId;
    }

    public int getQuantityGood() {
        return quantityGood;
    }

    public int getQuantityDamaged() {
        return quantityDamaged;
    }
}
