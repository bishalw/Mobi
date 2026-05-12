package com.productinfo.controller;

import com.productinfo.dto.ProductDTO;
import com.productinfo.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/productapi")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/allProducts")
    public List<ProductDTO> fetchAllProducts() {
        return productService.getAllProducts();
    }

    @PostMapping("/saveProduct")
    public ProductDTO insertProduct(@Valid @RequestBody ProductDTO productDTO) {
        return productService.createProduct(productDTO);
    }

    @GetMapping("products/{id}")
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
