package com.mobi.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ShipmentNoticeRequest(
        @NotBlank @Size(max = 100) String carrier,
        @Size(max = 100) String trackingNumber,
        LocalDate expectedArrival,
        @NotEmpty List<@Valid @NotNull Line> lines) {

    public record Line(@NotNull Long poLineId, @NotNull @Min(1) @Max(100_000) Integer quantityShipped) {
    }
}
