package com.mobi.order.service;

import com.mobi.order.client.DownstreamCalls;
import com.mobi.order.client.InventoryClient;
import com.mobi.order.client.PaymentClient;
import com.mobi.order.client.ProductClient;
import com.mobi.order.dto.BatchReserveItemRequest;
import com.mobi.order.dto.BatchReserveRequest;
import com.mobi.order.dto.CreateOrderItemRequest;
import com.mobi.order.dto.CreateOrderRequest;
import com.mobi.order.dto.OrderItemResponse;
import com.mobi.order.dto.ChargeRequest;
import com.mobi.order.dto.OrderResponse;
import com.mobi.order.dto.PayOrderRequest;
import com.mobi.order.dto.PaymentResult;
import com.mobi.order.dto.RefundRequest;
import com.mobi.order.dto.ProductResponse;
import com.mobi.order.entity.CustomerOrder;
import com.mobi.order.entity.OrderItem;
import com.mobi.order.enums.OrderStatus;
import com.mobi.order.event.OrderEventPublisher;
import com.mobi.order.event.OrderEventType;
import com.mobi.order.exception.FeignErrorParser;
import com.mobi.order.exception.OrderCreationException;
import com.mobi.order.exception.PaymentDeclinedException;
import com.mobi.order.exception.ResourceNotFoundException;
import com.mobi.order.repository.OrderRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderIdempotencyService idempotencyService;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderAuthorizationService authorizationService;
    private final PaymentClient paymentClient;
    private final DownstreamCalls downstream;
    private final TransactionTemplate transactionTemplate;
    private final String currency;

    public OrderServiceImpl(OrderRepository orderRepository, ProductClient productClient, InventoryClient inventoryClient,
                            OrderIdempotencyService idempotencyService, OrderEventPublisher orderEventPublisher,
                            OrderAuthorizationService authorizationService, PaymentClient paymentClient,
                            DownstreamCalls downstream, TransactionTemplate transactionTemplate,
                            @Value("${payments.currency:USD}") String currency) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.idempotencyService = idempotencyService;
        this.orderEventPublisher = orderEventPublisher;
        this.authorizationService = authorizationService;
        this.paymentClient = paymentClient;
        this.downstream = downstream;
        this.transactionTemplate = transactionTemplate;
        this.currency = currency;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        authorizationService.requireOwnerOrAdmin(request.getUserId());
        var cachedResponse = idempotencyService.get(idempotencyKey);
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        CustomerOrder order = new CustomerOrder();
        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            ProductResponse product = downstream.product(() -> productClient.getProduct(itemRequest.getProductId()));
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

        List<BatchReserveItemRequest> reserveItems = request.getItems().stream()
                .map(i -> new BatchReserveItemRequest(i.getProductId(), i.getQuantity()))
                .toList();
        BatchReserveRequest reserveRequest = new BatchReserveRequest(
                savedOrder.getId().toString(), reserveItems);

        try {
            downstream.inventory(() -> inventoryClient.batchReserve(reserveRequest));
            savedOrder.setStatus(OrderStatus.INVENTORY_RESERVED);
            savedOrder = orderRepository.save(savedOrder);
            orderEventPublisher.publish(OrderEventType.ORDER_INVENTORY_RESERVED, savedOrder);
            OrderResponse response = mapToResponse(savedOrder);
            idempotencyService.save(idempotencyKey, response);
            return response;
        } catch (FeignException exp) {
            savedOrder.setStatus(OrderStatus.CANCELLED);
            savedOrder = orderRepository.save(savedOrder);
            orderEventPublisher.publish(OrderEventType.ORDER_CANCELLED, savedOrder);
            throw new OrderCreationException(
                    FeignErrorParser.extractMessage(exp, "Unable to reserve inventory for order"), exp);
        }
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        authorizationService.requireAdmin();
        return orderRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Override
    public OrderResponse getOrderById(Long id) {
        CustomerOrder order = findOrderById(id);
        authorizationService.requireOwnerOrAdmin(order);
        return mapToResponse(order);
    }

    @Override
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        authorizationService.requireOwnerOrAdmin(userId);
        return orderRepository.findByUserId(userId).stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        CustomerOrder order = findOrderById(id);
        authorizationService.requireOwnerOrAdmin(order);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new OrderCreationException("Confirmed orders cannot be cancelled");
        }
        if (order.getStatus() == OrderStatus.INVENTORY_RESERVED) {
            String reservationKey = order.getId().toString();
            try {
                downstream.inventory(() -> inventoryClient.releaseReservation(reservationKey));
            } catch (RuntimeException e) {
                // Includes an open circuit breaker. The reaper releases any reservation
                // left behind, so a cancel never fails just because inventory is down.
                log.warn("Could not release reservation {} now; the reaper will reclaim it: {}",
                        reservationKey, e.getMessage());
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        orderEventPublisher.publish(OrderEventType.ORDER_CANCELLED, order);
        return mapToResponse(order);
    }

    /**
     * Checkout's payment step, run as an orchestrated saga:
     *
     * <ol>
     *   <li>charge the card through payment-service;</li>
     *   <li>on success, turn the inventory reservation into a sale and mark the order CONFIRMED;</li>
     *   <li>if the reservation is already gone (it expired while the customer paid), refund the
     *       charge and cancel the order — the compensating step.</li>
     * </ol>
     *
     * <p>Deliberately not one database transaction: holding a row lock across two network calls
     * would serialise checkout behind the slowest card network. Each state change commits on its
     * own, together with its outbox event, and the optimistic lock on the order stops two
     * concurrent attempts from both moving it forward.
     */
    @Override
    public OrderResponse payForOrder(Long id, PayOrderRequest request) {
        CustomerOrder order = findOrderById(id);
        authorizationService.requireOwnerOrAdmin(order);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return mapToResponse(order);
        }
        if (order.getStatus() != OrderStatus.INVENTORY_RESERVED) {
            throw new OrderCreationException("Order cannot be paid in status " + order.getStatus());
        }

        // Stable across retries of the same attempt, so a timeout followed by a retry
        // returns the original charge instead of creating a second one.
        String idempotencyKey = "order-" + id + "-attempt-" + order.getPaymentAttempts();
        ChargeRequest charge = new ChargeRequest(order.getId(), order.getUserId(), order.getTotalAmount(),
                currency, request.paymentMethodToken(), idempotencyKey);

        PaymentResult payment;
        try {
            payment = downstream.payment(() -> paymentClient.charge(charge));
        } catch (FeignException.Conflict exp) {
            throw new OrderCreationException(
                    FeignErrorParser.extractMessage(exp, "A payment for this order is already in progress"), exp);
        }

        if (payment.declined()) {
            transactionTemplate.executeWithoutResult(status -> {
                CustomerOrder current = findOrderById(id);
                current.incrementPaymentAttempts();
                orderRepository.save(current);
            });
            throw new PaymentDeclinedException(payment.failureCode(),
                    payment.failureMessage() == null ? "Payment was declined" : payment.failureMessage());
        }
        if (!payment.succeeded()) {
            throw new OrderCreationException("Unexpected payment status " + payment.status());
        }

        try {
            downstream.inventory(() -> inventoryClient.confirmReservation(id.toString()));
        } catch (FeignException exp) {
            if (exp.status() >= 400 && exp.status() < 500) {
                return compensateExpiredReservation(id, payment, exp);
            }
            // Inventory is unreachable, not refusing. The money is taken and the reservation
            // may well still be valid, so record the payment and let the customer retry:
            // the same idempotency key returns this charge and the confirm is attempted again.
            recordPayment(id, payment.paymentId());
            throw exp;
        } catch (RuntimeException exp) {
            recordPayment(id, payment.paymentId());
            throw exp;
        }

        CustomerOrder confirmed = transactionTemplate.execute(status -> {
            CustomerOrder current = findOrderById(id);
            current.setPaymentId(payment.paymentId());
            current.setStatus(OrderStatus.CONFIRMED);
            CustomerOrder saved = orderRepository.save(current);
            orderEventPublisher.publish(OrderEventType.ORDER_CONFIRMED, saved);
            return saved;
        });
        return mapToResponse(confirmed);
    }

    private OrderResponse compensateExpiredReservation(Long id, PaymentResult payment, FeignException cause) {
        log.warn("Reservation for order {} could not be confirmed after payment {}; refunding",
                id, payment.paymentId());
        try {
            downstream.payment(() -> paymentClient.refund(payment.paymentId(),
                    new RefundRequest("Reservation expired before payment completed")));
        } catch (RuntimeException refundFailure) {
            // Leave the order as it is with the payment recorded, so the failure is visible
            // to an operator instead of silently keeping the customer's money.
            log.error("Refund of payment {} for order {} failed; manual follow-up required",
                    payment.paymentId(), id, refundFailure);
            recordPayment(id, payment.paymentId());
            throw new OrderCreationException("Payment taken but the order could not be completed; support has been notified", cause);
        }
        transactionTemplate.executeWithoutResult(status -> {
            CustomerOrder current = findOrderById(id);
            current.setPaymentId(payment.paymentId());
            current.setStatus(OrderStatus.CANCELLED);
            CustomerOrder saved = orderRepository.save(current);
            orderEventPublisher.publish(OrderEventType.ORDER_CANCELLED, saved);
        });
        throw new OrderCreationException(
                "Your reservation expired before payment completed. The payment has been refunded.", cause);
    }

    private void recordPayment(Long id, String paymentId) {
        transactionTemplate.executeWithoutResult(status -> {
            CustomerOrder current = findOrderById(id);
            current.setPaymentId(paymentId);
            orderRepository.save(current);
        });
    }

    /**
     * Admin refund of a paid order. Order-service owns the order's state, so the refund
     * goes through here and both sides change together: the payment is refunded first,
     * then the order is marked REFUNDED. Refunds are idempotent in payment-service, so
     * if marking the order fails, retrying the request is safe.
     *
     * <p>With {@code restock}, the sold quantities also go back into available stock
     * (for goods that came back). That step is idempotent in inventory-service too, and
     * the order is only marked REFUNDED once both have succeeded, so a failure part way
     * leaves the order CONFIRMED and the whole request can simply be retried.
     *
     * <p>No HTTP call is made inside a database transaction.
     */
    @Override
    public OrderResponse refundOrder(Long id, String reason, boolean restock) {
        authorizationService.requireAdmin();
        CustomerOrder order = findOrderById(id);
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return mapToResponse(order);
        }
        if (order.getStatus() != OrderStatus.CONFIRMED || order.getPaymentId() == null) {
            throw new OrderCreationException("Only a paid, confirmed order can be refunded; this one is " + order.getStatus());
        }
        String paymentId = order.getPaymentId();
        String why = reason == null || reason.isBlank() ? "Refunded by an administrator" : reason.trim();
        downstream.payment(() -> paymentClient.refund(paymentId, new RefundRequest(why)));
        if (restock) {
            // The reservation key is the order id; see createOrder.
            downstream.inventory(() -> inventoryClient.returnReservation(id.toString()));
        }

        CustomerOrder refunded = transactionTemplate.execute(status -> {
            CustomerOrder current = findOrderById(id);
            current.setStatus(OrderStatus.REFUNDED);
            current.setRestocked(restock);
            return orderRepository.save(current);
        });
        log.info("Order {} refunded (payment {}, restocked={})", id, paymentId, restock);
        return mapToResponse(refunded);
    }

    /**
     * Manual override for operators: confirms an order without taking payment, for
     * example after payment was collected out of band. Customers go through
     * {@link #payForOrder}; this used to be open to them, which let anyone skip paying.
     */
    @Override
    @Transactional
    public OrderResponse confirmOrder(Long id) {
        authorizationService.requireAdmin();
        CustomerOrder order = findOrderById(id);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return mapToResponse(order);
        }
        if (order.getStatus() != OrderStatus.INVENTORY_RESERVED) {
            throw new OrderCreationException("Order cannot be confirmed in status " + order.getStatus());
        }
        try {
            downstream.inventory(() -> inventoryClient.confirmReservation(id.toString()));
        } catch (FeignException exp) {
            throw new OrderCreationException(
                    FeignErrorParser.extractMessage(exp, "Unable to confirm inventory reservation for order"), exp);
        }
        order.setStatus(OrderStatus.CONFIRMED);
        order = orderRepository.save(order);
        orderEventPublisher.publish(OrderEventType.ORDER_CONFIRMED, order);
        return mapToResponse(order);
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
        response.setPaymentId(order.getPaymentId());
        response.setRestocked(order.isRestocked());
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
