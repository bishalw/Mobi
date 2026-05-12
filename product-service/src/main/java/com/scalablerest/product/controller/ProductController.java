package com.scalablerest.product.controller;

import com.scalablerest.product.dto.ProductDTO;
import com.scalablerest.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/productapi")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/allProducts")
    public List<ProductDTO> fetchAllProducts() {
        return productService.getAllProducts();
    }

    @PostMapping("/saveProduct")
    public ProductDTO insertProduct(@Valid @RequestBody ProductDTO productDTO) {
        return productService.createProduct(productDTO);
    }

    @GetMapping("/products/{id}")
    public ProductDTO fetchProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @GetMapping("/products")
    public List<ProductDTO> fetchProducts() {
        return productService.getAllProducts();
    }

    @PutMapping("/updateProduct/{id}")
    public ProductDTO modifyProduct(@PathVariable Long id, @Valid @RequestBody ProductDTO productDTO) {
        return productService.updateProduct(id, productDTO);
    }

    @DeleteMapping("/deleteProduct/{id}")
    public String removeProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return "Product delete successfully";
    }
}
