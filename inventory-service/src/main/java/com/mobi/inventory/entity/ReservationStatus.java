package com.mobi.inventory.entity;

public enum ReservationStatus {
    ACTIVE,
    CONFIRMED,
    RELEASED,
    EXPIRED,
    // Confirmed (sold), then put back on the shelf after a refund with restock.
    RETURNED
}
