package com.scalablerest.order.client;

import com.scalablerest.order.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "productClient", url = "${services.product-service.url}")
public interface ProductClient {

    @GetMapping("/productapi/products/{id}")
    ProductResponse getProduct(@PathVariable Long id);
}
