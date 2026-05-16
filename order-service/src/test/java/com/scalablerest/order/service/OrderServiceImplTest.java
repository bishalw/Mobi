package com.scalablerest.order.service;

import com.scalablerest.order.client.InventoryClient;
import com.scalablerest.order.client.ProductClient;
import com.scalablerest.order.entity.CustomerOrder;
import com.scalablerest.order.enums.OrderStatus;
import com.scalablerest.order.event.OrderEventPublisher;
import com.scalablerest.order.event.OrderEventType;
import com.scalablerest.order.exception.OrderCreationException;
import com.scalablerest.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private OrderRepository orderRepository;
    private InventoryClient inventoryClient;
    private OrderEventPublisher orderEventPublisher;
    private OrderAuthorizationService authorizationService;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        ProductClient productClient = mock(ProductClient.class);
        inventoryClient = mock(InventoryClient.class);
        OrderIdempotencyService idempotencyService = mock(OrderIdempotencyService.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        authorizationService = mock(OrderAuthorizationService.class);
        service = new OrderServiceImpl(orderRepository, productClient, inventoryClient, idempotencyService, orderEventPublisher, authorizationService);
    }

    @Test
    void confirmOrderConfirmsReservedOrder() {
        CustomerOrder order = order(42L, OrderStatus.INVENTORY_RESERVED);
        CustomerOrder saved = order(42L, OrderStatus.CONFIRMED);

        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(saved);

        var response = service.confirmOrder(42L);

        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        verify(authorizationService).requireOwnerOrAdmin(order);
        verify(inventoryClient).confirmReservation("42");
        verify(orderEventPublisher).publish(OrderEventType.ORDER_CONFIRMED, saved);
    }

    @Test
    void confirmOrderTreatsAlreadyConfirmedAsNoOp() {
        CustomerOrder order = order(42L, OrderStatus.CONFIRMED);

        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        var response = service.confirmOrder(42L);

        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        verify(authorizationService).requireOwnerOrAdmin(order);
        verify(inventoryClient, never()).confirmReservation("42");
        verify(orderRepository, never()).save(order);
        verify(orderEventPublisher, never()).publish(OrderEventType.ORDER_CONFIRMED, order);
    }

    @Test
    void confirmOrderRejectsInvalidState() {
        CustomerOrder order = order(42L, OrderStatus.PENDING);

        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        assertThrows(OrderCreationException.class, () -> service.confirmOrder(42L));
        verify(authorizationService).requireOwnerOrAdmin(order);
        verify(inventoryClient, never()).confirmReservation("42");
    }

    @Test
    void cancelOrderRejectsConfirmedOrder() {
        CustomerOrder order = order(42L, OrderStatus.CONFIRMED);

        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        assertThrows(OrderCreationException.class, () -> service.cancelOrder(42L));
        verify(authorizationService).requireOwnerOrAdmin(order);
        verify(inventoryClient, never()).releaseReservation("42");
    }

    private CustomerOrder order(Long id, OrderStatus status) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setUserId(7L);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.TEN);
        return order;
    }
}
