package com.mobi.order.controller;

import com.mobi.order.dto.AdminOrderResponse;
import com.mobi.order.dto.CustomerSummaryResponse;
import com.mobi.order.dto.OrderStatsResponse;
import com.mobi.order.dto.PageResponse;
import com.mobi.order.service.AdminOrderService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Admin views over orders. Authorisation is enforced in the service by requireAdmin(),
// matching how the rest of this service does it rather than relying on the path prefix.
@RestController
@RequestMapping("/orderapi/admin")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping("/stats")
    public OrderStatsResponse getStats() {
        return adminOrderService.getStats();
    }

    // Default sort is the question this page exists to answer: who orders the most.
    @GetMapping("/customers")
    public PageResponse<CustomerSummaryResponse> listCustomers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "orderCount", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminOrderService.listCustomers(search, pageable);
    }

    @GetMapping("/orders")
    public PageResponse<AdminOrderResponse> listOrders(
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminOrderService.listOrders(pageable);
    }
}
