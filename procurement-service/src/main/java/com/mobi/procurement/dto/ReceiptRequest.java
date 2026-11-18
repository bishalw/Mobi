package com.mobi.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** One count against a PO: good and damaged units per line. Lines with nothing counted may be left out. */
public record ReceiptRequest(
        @Size(max = 500) String note,
        @NotEmpty List<@Valid @NotNull Line> lines) {

    // Capped like PO quantities, so good + damaged can't overflow an int.
    public record Line(@NotNull Long poLineId,
                       @NotNull @Min(0) @Max(100_000) Integer quantityGood,
                       @NotNull @Min(0) @Max(100_000) Integer quantityDamaged) {
    }
}
