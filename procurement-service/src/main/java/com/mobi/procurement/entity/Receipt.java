package com.mobi.procurement.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * What the warehouse counted in against a PO in one go. {@code receiptKey} is the client's
 * Idempotency-Key, unique, so a retried submit can't count the same goods twice.
 */
@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String receiptKey;

    @Column(name = "po_id", nullable = false)
    private Long poId;

    @Column(nullable = false, length = 100)
    private String receivedBy;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column(length = 500)
    private String note;

    // SHA-256 of the counted lines; see PurchaseOrderService.receive.
    @Column(length = 64)
    private String requestHash;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ReceiptLine> lines = new ArrayList<>();

    protected Receipt() {
    }

    public Receipt(String receiptKey, Long poId, String receivedBy, String note, String requestHash) {
        this.receiptKey = receiptKey;
        this.poId = poId;
        this.receivedBy = receivedBy;
        this.note = note;
        this.requestHash = requestHash;
    }

    @PrePersist
    void onCreate() {
        receivedAt = LocalDateTime.now();
    }

    public void addLine(Long poLineId, int good, int damaged) {
        lines.add(new ReceiptLine(this, poLineId, good, damaged));
    }

    public Long getId() {
        return id;
    }

    public String getReceiptKey() {
        return receiptKey;
    }

    public Long getPoId() {
        return poId;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getNote() {
        return note;
    }

    public List<ReceiptLine> getLines() {
        return lines;
    }
}
