package com.mobi.order.dto;

public class InventoryQuantityRequest {

    private Integer quantity;

    public InventoryQuantityRequest() {
    }

    public InventoryQuantityRequest(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
