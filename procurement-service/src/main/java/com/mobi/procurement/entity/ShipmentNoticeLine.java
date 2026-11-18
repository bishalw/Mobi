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
@Table(name = "shipment_notice_lines")
public class ShipmentNoticeLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private ShipmentNotice notice;

    @Column(name = "po_line_id", nullable = false)
    private Long poLineId;

    @Column(nullable = false)
    private int quantityShipped;

    protected ShipmentNoticeLine() {
    }

    ShipmentNoticeLine(ShipmentNotice notice, Long poLineId, int quantityShipped) {
        this.notice = notice;
        this.poLineId = poLineId;
        this.quantityShipped = quantityShipped;
    }

    public Long getId() {
        return id;
    }

    public Long getPoLineId() {
        return poLineId;
    }

    public int getQuantityShipped() {
        return quantityShipped;
    }
}
