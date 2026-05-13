package com.scalablerest.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ProductRequest {

    @NotBlank(message = "SKU is required")
    @Size(min = 3, max = 64, message = "SKU must be between 3 and 64 characters")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than 0")
    private BigDecimal price;

    @NotNull(message = "Active flag is required")
    private Boolean active = true;

    public ProductRequest() {
    }

    public ProductRequest(String sku, String name, String description, BigDecimal price, Boolean active) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.active = active;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
