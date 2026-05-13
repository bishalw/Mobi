package com.scalablerest.order.client;

import com.scalablerest.order.dto.InventoryQuantityRequest;
import com.scalablerest.order.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventoryClient", url = "${services.inventory-service.url}")
public interface InventoryClient {

    @PostMapping("/inventoryapi/products/{productId}/reserve")
    InventoryResponse reserve(@PathVariable Long productId, @RequestBody InventoryQuantityRequest request);

    @PostMapping("/inventoryapi/products/{productId}/release")
    InventoryResponse release(@PathVariable Long productId, @RequestBody InventoryQuantityRequest request);
}
