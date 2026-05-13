package com.scalablerest.product.service;

import com.scalablerest.product.dto.ProductRequest;
import com.scalablerest.product.dto.ProductResponse;
import com.scalablerest.product.entity.Product;
import com.scalablerest.product.exception.ResourceNotFoundException;
import com.scalablerest.product.mapper.ProductMapper;
import com.scalablerest.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = ProductMapper.mapToEntity(productRequest);
        return ProductMapper.mapToResponse(productRepository.save(product));
    }

    @Override
    public ProductResponse getProductById(Long id) {
        Product product = findProductById(id);
        return ProductMapper.mapToResponse(product);
    }

    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(ProductMapper::mapToResponse)
                .toList();
    }

    @Override
    public ProductResponse updateProduct(Long id, ProductRequest productRequest) {
        Product existingProduct = findProductById(id);
        existingProduct.setSku(productRequest.getSku());
        existingProduct.setName(productRequest.getName());
        existingProduct.setDescription(productRequest.getDescription());
        existingProduct.setPrice(productRequest.getPrice());
        existingProduct.setActive(productRequest.getActive());
        return ProductMapper.mapToResponse(productRepository.save(existingProduct));
    }

    @Override
    public void deleteProduct(Long id) {
        Product existingProduct = findProductById(id);
        productRepository.delete(existingProduct);
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }
}
