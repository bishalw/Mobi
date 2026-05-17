package com.mobi.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class BatchReserveRequest {

    @NotBlank(message = "Reservation key is required")
    private String reservationKey;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<BatchReserveItemRequest> items;

    public BatchReserveRequest() {
    }

    public String getReservationKey() {
        return reservationKey;
    }

    public void setReservationKey(String reservationKey) {
        this.reservationKey = reservationKey;
    }

    public List<BatchReserveItemRequest> getItems() {
        return items;
    }

    public void setItems(List<BatchReserveItemRequest> items) {
        this.items = items;
    }
}
