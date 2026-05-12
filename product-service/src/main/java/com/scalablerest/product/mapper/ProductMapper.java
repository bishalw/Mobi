package com.scalablerest.product.mapper;

import com.scalablerest.product.dto.ProductRequest;
import com.scalablerest.product.dto.ProductResponse;
import com.scalablerest.product.entity.Product;

public class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse mapToResponse(Product product) {
        ProductResponse productResponse = new ProductResponse();
        productResponse.setId(product.getId());
        productResponse.setName(product.getName());
        productResponse.setPrice(product.getPrice());
        productResponse.setQuantity(product.getQuantity());
        return productResponse;
    }

    public static Product mapToEntity(ProductRequest productRequest) {
        Product product = new Product();
        product.setName(productRequest.getName());
        product.setPrice(productRequest.getPrice());
        product.setQuantity(productRequest.getQuantity());
        return product;
    }
}
