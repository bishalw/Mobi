package com.mobi.order.dto;

import java.util.List;

public class BatchReserveRequest {

    private String reservationKey;
    private List<BatchReserveItemRequest> items;

    public BatchReserveRequest() {
    }

    public BatchReserveRequest(String reservationKey, List<BatchReserveItemRequest> items) {
        this.reservationKey = reservationKey;
        this.items = items;
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
