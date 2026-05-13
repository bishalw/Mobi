package com.scalablerest.order.service;

import com.scalablerest.order.client.InventoryClient;
import com.scalablerest.order.client.ProductClient;
import com.scalablerest.order.dto.CreateOrderItemRequest;
import com.scalablerest.order.dto.CreateOrderRequest;
import com.scalablerest.order.dto.InventoryQuantityRequest;
import com.scalablerest.order.dto.OrderItemResponse;
import com.scalablerest.order.dto.OrderResponse;
import com.scalablerest.order.dto.ProductResponse;
import com.scalablerest.order.entity.CustomerOrder;
import com.scalablerest.order.entity.OrderItem;
import com.scalablerest.order.enums.OrderStatus;
import com.scalablerest.order.exception.OrderCreationException;
import com.scalablerest.order.exception.ResourceNotFoundException;
import com.scalablerest.order.repository.OrderRepository;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    public OrderServiceImpl(OrderRepository orderRepository, ProductClient productClient, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        CustomerOrder order = new CustomerOrder();
        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            ProductResponse product = productClient.getProduct(itemRequest.getProductId());
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new OrderCreationException("Product is not active: " + itemRequest.getProductId());
            }

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductNameSnapshot(product.getName());
            item.setUnitPriceSnapshot(product.getPrice());
            item.setQuantity(itemRequest.getQuantity());
            item.setLineTotal(lineTotal);
            order.addItem(item);
            total = total.add(lineTotal);
        }
        order.setTotalAmount(total);
        CustomerOrder savedOrder = orderRepository.save(order);

        List<CreateOrderItemRequest> reservedItems = new ArrayList<>();
        try {
            for (CreateOrderItemRequest itemRequest : request.getItems()) {
                inventoryClient.reserve(itemRequest.getProductId(), new InventoryQuantityRequest(itemRequest.getQuantity()));
                reservedItems.add(itemRequest);
            }
            savedOrder.setStatus(OrderStatus.INVENTORY_RESERVED);
            return mapToResponse(orderRepository.save(savedOrder));
        } catch (FeignException exp) {
            releaseReservedItems(reservedItems);
            savedOrder.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(savedOrder);
            throw new OrderCreationException("Unable to reserve inventory for order", exp);
        }
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Override
    public OrderResponse getOrderById(Long id) {
        return mapToResponse(findOrderById(id));
    }

    @Override
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        CustomerOrder order = findOrderById(id);
        if (order.getStatus() == OrderStatus.INVENTORY_RESERVED) {
            for (OrderItem item : order.getItems()) {
                inventoryClient.release(item.getProductId(), new InventoryQuantityRequest(item.getQuantity()));
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        return mapToResponse(orderRepository.save(order));
    }

    private void releaseReservedItems(List<CreateOrderItemRequest> reservedItems) {
        for (CreateOrderItemRequest itemRequest : reservedItems) {
            try {
                inventoryClient.release(itemRequest.getProductId(), new InventoryQuantityRequest(itemRequest.getQuantity()));
            } catch (FeignException ignored) {
                // Best-effort compensation. Kafka/Saga phase will make this more robust.
            }
        }
    }

    private CustomerOrder findOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    private OrderResponse mapToResponse(CustomerOrder order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setStatus(order.getStatus());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        response.setItems(order.getItems().stream().map(this::mapItemToResponse).toList());
        return response;
    }

    private OrderItemResponse mapItemToResponse(OrderItem item) {
        OrderItemResponse response = new OrderItemResponse();
        response.setId(item.getId());
        response.setProductId(item.getProductId());
        response.setProductNameSnapshot(item.getProductNameSnapshot());
        response.setUnitPriceSnapshot(item.getUnitPriceSnapshot());
        response.setQuantity(item.getQuantity());
        response.setLineTotal(item.getLineTotal());
        return response;
    }
}
