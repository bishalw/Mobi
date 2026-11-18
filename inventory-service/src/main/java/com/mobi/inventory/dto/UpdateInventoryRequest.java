package com.mobi.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Shelf details only; quantities change through adjustments. Null fields are left as they are. */
public record UpdateInventoryRequest(
        @Size(max = 100, message = "warehouseLocation cannot exceed 100 characters") String warehouseLocation,
        @Min(value = 0, message = "reorderLevel cannot be negative") Integer reorderLevel) {
}
