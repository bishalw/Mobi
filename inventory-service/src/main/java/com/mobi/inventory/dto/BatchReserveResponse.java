package com.mobi.inventory.dto;

import java.time.LocalDateTime;
import java.util.List;

public class BatchReserveResponse {

    private String reservationKey;
    private String status;
    private LocalDateTime reservedAt;
    private List<BatchReserveItemRequest> items;

    public BatchReserveResponse() {
    }

    public String getReservationKey() {
        return reservationKey;
    }

    public void setReservationKey(String reservationKey) {
        this.reservationKey = reservationKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(LocalDateTime reservedAt) {
        this.reservedAt = reservedAt;
    }

    public List<BatchReserveItemRequest> getItems() {
        return items;
    }

    public void setItems(List<BatchReserveItemRequest> items) {
        this.items = items;
    }
}
