package com.scalablerest.product.mapper;

import com.scalablerest.product.dto.ProductDTO;
import com.scalablerest.product.entity.Product;

public class ProductMapper {

    private ProductMapper() {
    }

    public static ProductDTO mapToDTO(Product product) {
        ProductDTO productDto = new ProductDTO();
        productDto.setId(product.getId());
        productDto.setName(product.getName());
        productDto.setPrice(product.getPrice());
        productDto.setQuantity(product.getQuantity());
        return productDto;
    }

    public static Product mapToEntity(ProductDTO productDTO) {
        Product product = new Product();
        if (productDTO.getId() != null) {
            product.setId(productDTO.getId());
        }
        product.setName(productDTO.getName());
        product.setPrice(productDTO.getPrice());
        product.setQuantity(productDTO.getQuantity());
        return product;
    }
}
