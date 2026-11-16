package com.mobi.procurement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** One product a supplier sells us, at its current unit cost. */
@Entity
@Table(name = "supplier_products")
@IdClass(SupplierProduct.Key.class)
public class SupplierProduct {

    @Id
    @Column(name = "supplier_id")
    private Long supplierId;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(length = 100)
    private String supplierSku;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    protected SupplierProduct() {
    }

    public SupplierProduct(Long supplierId, Long productId, String supplierSku, BigDecimal unitCost) {
        this.supplierId = supplierId;
        this.productId = productId;
        this.supplierSku = supplierSku;
        this.unitCost = unitCost;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSupplierSku() {
        return supplierSku;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public static class Key implements Serializable {
        private Long supplierId;
        private Long productId;

        public Key() {
        }

        public Key(Long supplierId, Long productId) {
            this.supplierId = supplierId;
            this.productId = productId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(supplierId, k.supplierId) && Objects.equals(productId, k.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(supplierId, productId);
        }
    }
}
