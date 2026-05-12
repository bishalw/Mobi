package com.scalablerest.product.service;

import com.scalablerest.product.dto.ProductDTO;

import java.util.List;

public interface ProductService {

    ProductDTO createProduct(ProductDTO productDTO);

    ProductDTO getProductById(Long id);

    List<ProductDTO> getAllProducts();

    ProductDTO updateProduct(Long id, ProductDTO productDTO);

    void deleteProduct(Long id);
}
