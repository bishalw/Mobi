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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A supplier's "we shipped this". Informational: it never changes stock. */
@Entity
@Table(name = "shipment_notices")
public class ShipmentNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_id", nullable = false)
    private Long poId;

    @Column(nullable = false, length = 100)
    private String carrier;

    @Column(length = 100)
    private String trackingNumber;

    @Column
    private LocalDate expectedArrival;

    @Column(nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "notice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ShipmentNoticeLine> lines = new ArrayList<>();

    protected ShipmentNotice() {
    }

    public ShipmentNotice(Long poId, String carrier, String trackingNumber, LocalDate expectedArrival, String createdBy) {
        this.poId = poId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.expectedArrival = expectedArrival;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void addLine(Long poLineId, int quantityShipped) {
        lines.add(new ShipmentNoticeLine(this, poLineId, quantityShipped));
    }

    public Long getId() {
        return id;
    }

    public Long getPoId() {
        return poId;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public LocalDate getExpectedArrival() {
        return expectedArrival;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<ShipmentNoticeLine> getLines() {
        return lines;
    }
}
