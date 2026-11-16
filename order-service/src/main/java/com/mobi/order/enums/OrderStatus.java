package com.mobi.order.enums;

public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    CONFIRMED,
    CANCELLED,
    // Paid, then refunded by an operator. Stock is not returned automatically: the
    // goods may already have shipped, so restocking is a separate inventory decision.
    REFUNDED
}
