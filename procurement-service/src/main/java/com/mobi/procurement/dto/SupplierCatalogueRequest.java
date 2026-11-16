package com.mobi.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** The supplier's whole catalogue; replaces what was there. */
public record SupplierCatalogueRequest(@NotNull List<@Valid @NotNull SupplierProductRequest> products) {
}
