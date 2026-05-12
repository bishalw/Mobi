package com.scalablerest.product.service;

import com.scalablerest.product.dto.ProductDTO;
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
    public ProductDTO createProduct(ProductDTO productDTO) {
        Product product = ProductMapper.mapToEntity(productDTO);
        return ProductMapper.mapToDTO(productRepository.save(product));
    }

    @Override
    public ProductDTO getProductById(Long id) {
        Product product = findProductById(id);
        return ProductMapper.mapToDTO(product);
    }

    @Override
    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(ProductMapper::mapToDTO)
                .toList();
    }

    @Override
    public ProductDTO updateProduct(Long id, ProductDTO productDTO) {
        Product existingProduct = findProductById(id);
        existingProduct.setName(productDTO.getName());
        existingProduct.setPrice(productDTO.getPrice());
        existingProduct.setQuantity(productDTO.getQuantity());
        return ProductMapper.mapToDTO(productRepository.save(existingProduct));
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
