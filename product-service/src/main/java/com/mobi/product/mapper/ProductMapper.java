package com.mobi.product.mapper;

import com.mobi.product.dto.ProductRequest;
import com.mobi.product.dto.ProductResponse;
import com.mobi.product.entity.Product;

public class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse mapToResponse(Product product) {
        ProductResponse productResponse = new ProductResponse();
        productResponse.setId(product.getId());
        productResponse.setSku(product.getSku());
        productResponse.setName(product.getName());
        productResponse.setDescription(product.getDescription());
        productResponse.setPrice(product.getPrice());
        productResponse.setActive(product.isActive());
        productResponse.setCreatedAt(product.getCreatedAt());
        productResponse.setUpdatedAt(product.getUpdatedAt());
        return productResponse;
    }

    public static Product mapToEntity(ProductRequest productRequest) {
        Product product = new Product();
        product.setSku(productRequest.getSku());
        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        product.setPrice(productRequest.getPrice());
        product.setActive(productRequest.getActive());
        return product;
    }
}
