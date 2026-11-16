package com.mobi.procurement.dto;

import com.mobi.procurement.entity.Supplier;

import java.time.LocalDateTime;

public record SupplierResponse(Long id, String name, String contactEmail, boolean active, LocalDateTime createdAt) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getContactEmail(),
                supplier.isActive(), supplier.getCreatedAt());
    }
}
