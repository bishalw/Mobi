package com.mobi.order.service;

import com.mobi.order.dto.CreateOrderRequest;
import com.mobi.order.dto.OrderResponse;
import com.mobi.order.dto.PayOrderRequest;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey);

    List<OrderResponse> getAllOrders();

    OrderResponse getOrderById(Long id);

    List<OrderResponse> getOrdersByUserId(Long userId);

    OrderResponse cancelOrder(Long id);

    OrderResponse payForOrder(Long id, PayOrderRequest request);

    OrderResponse confirmOrder(Long id);

    OrderResponse refundOrder(Long id, String reason, boolean restock);
}
