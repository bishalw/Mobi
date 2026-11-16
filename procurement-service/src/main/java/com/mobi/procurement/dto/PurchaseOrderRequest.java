package com.mobi.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Creates a DRAFT, or replaces one. A line's unit cost defaults to the supplier's catalogue price. */
public record PurchaseOrderRequest(
        @NotNull Long supplierId,
        LocalDate expectedDate,
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull Line> lines) {

    public record Line(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(100_000) Integer quantity,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {
    }
}
