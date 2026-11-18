package com.mobi.procurement.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** The date is checked in the service, allowing for the supplier's time zone. */
public record AcceptPurchaseOrderRequest(@NotNull LocalDate expectedDate) {
}
