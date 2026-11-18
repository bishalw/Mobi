package com.mobi.procurement.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A purchase order to one supplier. Status changes go through
 * {@link com.mobi.procurement.service.PurchaseOrderStateMachine}; this class only records
 * them. {@code @Version} turns two people acting on one PO at once into a 409.
 */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // PO-000123, derived from the id once it is known (see assignPoNumber). A unique
    // placeholder fills the column until then: "P-" + a UUID is 38 characters, inside
    // the column's 40 (MySQL's strict mode rejects anything longer).
    @Column(nullable = false, unique = true, length = 40)
    private String poNumber = "P-" + UUID.randomUUID();

    @Column(nullable = false)
    private Long supplierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column
    private LocalDate expectedDate;

    @Column(length = 1000)
    private String notes;

    // Why it was rejected or closed short; shown alongside the status.
    @Column(length = 500)
    private String statusNote;

    @Column(nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime sentAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime closedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(Long supplierId, LocalDate expectedDate, String notes, String createdBy) {
        this.supplierId = supplierId;
        this.expectedDate = expectedDate;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Marks the PO itself as changed. Line totals live on child rows, and changing those
     * alone neither dirties the PO nor bumps its @Version; touching it does, so two
     * concurrent shipments or receipts on one PO conflict instead of both passing the
     * quantity checks.
     */
    public void touch() {
        updatedAt = LocalDateTime.now();
    }

    public void assignPoNumber() {
        this.poNumber = "PO-%06d".formatted(id);
    }

    /** Replaces supplier, dates and lines; only while DRAFT (checked by the caller). */
    public void revise(Long supplierId, LocalDate expectedDate, String notes) {
        this.supplierId = supplierId;
        this.expectedDate = expectedDate;
        this.notes = notes;
    }

    public void clearLines() {
        lines.clear();
    }

    public void addLine(PurchaseOrderLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public Optional<PurchaseOrderLine> line(Long lineId) {
        return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst();
    }

    public BigDecimal total() {
        return lines.stream().map(PurchaseOrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean fullyAccountedFor() {
        return lines.stream().allMatch(line -> line.outstanding() == 0);
    }

    public void moveTo(PurchaseOrderStatus next, String note) {
        LocalDateTime now = LocalDateTime.now();
        switch (next) {
            case SENT -> sentAt = now;
            case ACCEPTED -> acceptedAt = now;
            case REJECTED -> rejectedAt = now;
            case CANCELLED -> cancelledAt = now;
            case RECEIVED -> closedAt = now;
            default -> {
            }
        }
        if (note != null) {
            statusNote = note;
        }
        status = next;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
    }

    public Long getId() {
        return id;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public String getNotes() {
        return notes;
    }

    public String getStatusNote() {
        return statusNote;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<PurchaseOrderLine> getLines() {
        return lines;
    }
}
