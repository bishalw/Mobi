package com.productinfo.service;

import com.productinfo.dto.ProductDTO;
import com.productinfo.entity.Product;
import com.productinfo.exception.ResourceNotFoundException;
import com.productinfo.mapper.ProductMapper;
import com.productinfo.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private  ProductRepository productRepository;


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
                .collect(Collectors.toList());
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
