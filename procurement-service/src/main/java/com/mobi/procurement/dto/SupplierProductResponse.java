package com.mobi.procurement.dto;

import com.mobi.procurement.entity.SupplierProduct;

import java.math.BigDecimal;

public record SupplierProductResponse(Long productId, String supplierSku, BigDecimal unitCost) {

    public static SupplierProductResponse from(SupplierProduct product, boolean showCost) {
        return new SupplierProductResponse(product.getProductId(), product.getSupplierSku(),
                showCost ? product.getUnitCost() : null);
    }
}
