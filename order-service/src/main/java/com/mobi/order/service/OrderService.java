package com.mobi.order.service;

import com.mobi.order.dto.CreateOrderRequest;
import com.mobi.order.dto.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey);

    List<OrderResponse> getAllOrders();

    OrderResponse getOrderById(Long id);

    List<OrderResponse> getOrdersByUserId(Long userId);

    OrderResponse cancelOrder(Long id);

    OrderResponse confirmOrder(Long id);
}
