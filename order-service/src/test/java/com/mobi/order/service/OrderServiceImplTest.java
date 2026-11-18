package com.mobi.order.service;

import com.mobi.order.client.DownstreamCalls;
import com.mobi.order.client.InventoryClient;
import com.mobi.order.client.PaymentClient;
import com.mobi.order.client.ProductClient;
import com.mobi.order.dto.ChargeRequest;
import com.mobi.order.dto.PayOrderRequest;
import com.mobi.order.dto.PaymentResult;
import com.mobi.order.dto.RefundRequest;
import com.mobi.order.exception.PaymentDeclinedException;
import feign.FeignException;
import feign.Request;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.mobi.order.entity.CustomerOrder;
import com.mobi.order.enums.OrderStatus;
import com.mobi.order.event.OrderEventPublisher;
import com.mobi.order.event.OrderEventType;
import com.mobi.order.exception.OrderCreationException;
import com.mobi.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private OrderRepository orderRepository;
    private InventoryClient inventoryClient;
    private OrderEventPublisher orderEventPublisher;
    private OrderAuthorizationService authorizationService;
    private PaymentClient paymentClient;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        ProductClient productClient = mock(ProductClient.class);
        inventoryClient = mock(InventoryClient.class);
        OrderIdempotencyService idempotencyService = mock(OrderIdempotencyService.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        authorizationService = mock(OrderAuthorizationService.class);
        paymentClient = mock(PaymentClient.class);
        service = new OrderServiceImpl(orderRepository, productClient, inventoryClient, idempotencyService,
                orderEventPublisher, authorizationService, paymentClient,
                new DownstreamCalls(CircuitBreakerRegistry.ofDefaults()),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), "USD");
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void confirmOrderConfirmsReservedOrder() {
        CustomerOrder order = order(42L, OrderStatus.INVENTORY_RESERVED);
        CustomerOrder saved = order(42L, OrderStatus.CONFIRMED);

        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(saved);

        var response = service.confirmOrder(42L);

        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        verify(authorizationService).requireAdmin();
        verify(inventoryClient).confirmReservation("42");
        verify(orderEventPublisher).publish(OrderEventType.ORDER_CONFIRMED, saved);
    }

    @Test
    void confirmOrderTreatsAlreadyConfirmedAsNoOp() {
        CustomerOrder order = order(42L, OrderStatus.CONFIRMED);

        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        var response = service.confirmOrder(42L);

        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        verify(authorizationService).requireAdmin();
        verify(inventoryClient, never()).confirmReservation("42");
        verify(orderRepository, never()).save(order);
        verify(orderEventPublisher, never()).publish(OrderEventType.ORDER_CONFIRMED, order);
    }

    @Test
    void confirmOrderRejectsInvalidState() {
        CustomerOrder order = order(42L, OrderStatus.PENDING);

        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        assertThrows(OrderCreationException.class, () -> service.confirmOrder(42L));
        verify(authorizationService).requireAdmin();
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

    @Test
    void payChargesThenConfirmsReservation() {
        CustomerOrder order = order(42L, OrderStatus.INVENTORY_RESERVED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentClient.charge(any())).thenReturn(payment("SUCCEEDED"));

        var response = service.payForOrder(42L, new PayOrderRequest("tok_visa"));

        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        assertEquals("pay-1", response.getPaymentId());
        verify(paymentClient).charge(new ChargeRequest(42L, 7L, BigDecimal.TEN, "USD", "tok_visa", "order-42-attempt-0"));
        verify(inventoryClient).confirmReservation("42");
        verify(orderEventPublisher).publish(eq(OrderEventType.ORDER_CONFIRMED), any());
    }

    @Test
    void declinedPaymentKeepsReservationAndAdvancesAttempt() {
        CustomerOrder order = order(42L, OrderStatus.INVENTORY_RESERVED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentClient.charge(any())).thenReturn(payment("DECLINED"));

        assertThrows(PaymentDeclinedException.class,
                () -> service.payForOrder(42L, new PayOrderRequest("tok_chargeDeclined")));

        assertEquals(OrderStatus.INVENTORY_RESERVED, order.getStatus());
        // The next attempt must use a fresh idempotency key, or payment-service would
        // just hand back this decline.
        assertEquals(1, order.getPaymentAttempts());
        verify(inventoryClient, never()).confirmReservation("42");
    }

    @Test
    void expiredReservationAfterPaymentIsRefundedAndCancelled() {
        CustomerOrder order = order(42L, OrderStatus.INVENTORY_RESERVED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentClient.charge(any())).thenReturn(payment("SUCCEEDED"));
        doThrow(feignError(409)).when(inventoryClient).confirmReservation("42");

        assertThrows(OrderCreationException.class, () -> service.payForOrder(42L, new PayOrderRequest("tok_visa")));

        verify(paymentClient).refund(eq("pay-1"), any(RefundRequest.class));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderEventPublisher).publish(eq(OrderEventType.ORDER_CANCELLED), any());
    }

    @Test
    void inventoryOutageAfterPaymentRecordsPaymentWithoutRefund() {
        CustomerOrder order = order(42L, OrderStatus.INVENTORY_RESERVED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentClient.charge(any())).thenReturn(payment("SUCCEEDED"));
        doThrow(feignError(503)).when(inventoryClient).confirmReservation("42");

        assertThrows(FeignException.class, () -> service.payForOrder(42L, new PayOrderRequest("tok_visa")));

        verify(paymentClient, never()).refund(any(), any());
        assertEquals(OrderStatus.INVENTORY_RESERVED, order.getStatus());
        assertEquals("pay-1", order.getPaymentId());
    }

    @Test
    void payingConfirmedOrderIsNoOp() {
        CustomerOrder order = order(42L, OrderStatus.CONFIRMED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        var response = service.payForOrder(42L, new PayOrderRequest("tok_visa"));

        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        assertNull(response.getPaymentId());
        verify(paymentClient, never()).charge(any());
    }

    @Test
    void adminRefundRefundsPaymentThenMarksOrderRefunded() {
        CustomerOrder order = order(42L, OrderStatus.CONFIRMED);
        order.setPaymentId("pay-1");
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentClient.refund(eq("pay-1"), any())).thenReturn(payment("REFUNDED"));

        var response = service.refundOrder(42L, "Damaged in transit", false);

        assertEquals(OrderStatus.REFUNDED, response.getStatus());
        verify(authorizationService).requireAdmin();
        verify(paymentClient).refund("pay-1", new RefundRequest("Damaged in transit"));
        verify(inventoryClient, never()).returnReservation(any());
        assertFalse(response.isRestocked());
    }

    @Test
    void refundWithRestockReturnsStockBeforeMarkingRefunded() {
        CustomerOrder order = order(42L, OrderStatus.CONFIRMED);
        order.setPaymentId("pay-1");
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentClient.refund(eq("pay-1"), any())).thenReturn(payment("REFUNDED"));

        var response = service.refundOrder(42L, null, true);

        var inOrder = inOrder(paymentClient, inventoryClient);
        inOrder.verify(paymentClient).refund(eq("pay-1"), any());
        inOrder.verify(inventoryClient).returnReservation("42");
        assertEquals(OrderStatus.REFUNDED, response.getStatus());
        assertTrue(response.isRestocked());
    }

    @Test
    void failedRestockLeavesOrderConfirmedSoTheRefundCanBeRetried() {
        CustomerOrder order = order(42L, OrderStatus.CONFIRMED);
        order.setPaymentId("pay-1");
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentClient.refund(eq("pay-1"), any())).thenReturn(payment("REFUNDED"));
        org.mockito.Mockito.doThrow(feignError(503)).when(inventoryClient).returnReservation("42");

        assertThrows(FeignException.class, () -> service.refundOrder(42L, null, true));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void refundIsRefusedForUnpaidOrdersAndRepeatsAreNoOps() {
        CustomerOrder unpaid = order(42L, OrderStatus.CONFIRMED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(unpaid));
        assertThrows(OrderCreationException.class, () -> service.refundOrder(42L, null, false));

        CustomerOrder done = order(43L, OrderStatus.REFUNDED);
        done.setPaymentId("pay-1");
        when(orderRepository.findById(43L)).thenReturn(Optional.of(done));
        assertEquals(OrderStatus.REFUNDED, service.refundOrder(43L, null, false).getStatus());

        verify(paymentClient, never()).refund(any(), any());
    }

    @Test
    void refundStopsIfPaymentRefundFails() {
        CustomerOrder order = order(42L, OrderStatus.CONFIRMED);
        order.setPaymentId("pay-1");
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentClient.refund(eq("pay-1"), any())).thenThrow(feignError(503));

        assertThrows(FeignException.class, () -> service.refundOrder(42L, null, false));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    private PaymentResult payment(String status) {
        return new PaymentResult("pay-1", 42L, status, BigDecimal.TEN, "USD",
                "DECLINED".equals(status) ? "card_declined" : null,
                "DECLINED".equals(status) ? "Your card was declined." : null);
    }

    private FeignException feignError(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "/inventoryapi/reservations/42/confirm",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("confirm", feign.Response.builder()
                .status(status).reason("x").request(request).headers(Map.of()).build());
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
