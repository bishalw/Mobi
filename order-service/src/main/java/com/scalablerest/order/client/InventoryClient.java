package com.scalablerest.order.client;

import com.scalablerest.order.dto.BatchReserveRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventoryClient", url = "${services.inventory-service.url}")
public interface InventoryClient {

    @PostMapping("/inventoryapi/reservations")
    void batchReserve(@RequestBody BatchReserveRequest request);

    @PostMapping("/inventoryapi/reservations/{reservationKey}/release")
    void releaseReservation(@PathVariable String reservationKey);
}
