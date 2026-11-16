package com.mobi.payment.service;

import com.mobi.payment.dto.ChargeRequest;
import com.mobi.payment.entity.Payment;
import com.mobi.payment.entity.PaymentStatus;
import com.mobi.payment.event.PaymentEventPublisher;
import com.mobi.payment.event.PaymentEventType;
import com.mobi.payment.exception.PaymentConflictException;
import com.mobi.payment.gateway.PaymentProviderException;
import com.mobi.payment.gateway.SimulatedPaymentProvider;
import com.mobi.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs PaymentService against an in-memory stand-in for the repository and the real
 * simulated provider, so the idempotency rules are exercised end to end.
 */
class PaymentServiceTest {

    private final Map<String, Payment> byKey = new HashMap<>();
    private PaymentRepository repository;
    private PaymentEventPublisher events;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentRepository.class);
        events = mock(PaymentEventPublisher.class);
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.onCreate();
            byKey.put(payment.getIdempotencyKey(), payment);
            return payment;
        });
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByIdempotencyKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(byKey.get(invocation.<String>getArgument(0))));
        when(repository.findByPaymentId(anyString())).thenAnswer(invocation -> byKey.values().stream()
                .filter(p -> p.getPaymentId().equals(invocation.getArgument(0))).findFirst());
        when(repository.findFirstByOrderIdAndStatus(any(), eq(PaymentStatus.SUCCEEDED))).thenAnswer(invocation ->
                byKey.values().stream().filter(p -> p.getOrderId().equals(invocation.getArgument(0))
                        && p.getStatus() == PaymentStatus.SUCCEEDED).findFirst());

        service = new PaymentService(repository, new SimulatedPaymentProvider(0, 0), CircuitBreaker.ofDefaults("test"),
                events, mock(PaymentAuthorization.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), new SimpleMeterRegistry(), 30_000);
    }

    @Test
    void approvedChargeSucceedsAndPublishesEvent() {
        var response = service.charge(request("tok_visa", "order-1-attempt-0"));

        assertEquals(PaymentStatus.SUCCEEDED, response.status());
        assertEquals("4242", response.cardLast4());
        verify(events).publish(eq(PaymentEventType.PAYMENT_SUCCEEDED), any());
    }

    @Test
    void declinedChargeIsReportedNotThrown() {
        var response = service.charge(request("tok_chargeDeclinedInsufficientFunds", "order-1-attempt-0"));

        assertEquals(PaymentStatus.DECLINED, response.status());
        assertEquals("insufficient_funds", response.failureCode());
        verify(events).publish(eq(PaymentEventType.PAYMENT_DECLINED), any());
    }

    @Test
    void retryWithSameKeyReturnsOriginalResultWithoutChargingAgain() {
        var first = service.charge(request("tok_visa", "order-1-attempt-0"));
        var second = service.charge(request("tok_visa", "order-1-attempt-0"));

        assertEquals(first.paymentId(), second.paymentId());
        verify(repository, org.mockito.Mockito.times(1)).saveAndFlush(any());
    }

    @Test
    void secondSuccessfulChargeForSameOrderIsRefusedEvenWithNewKey() {
        var first = service.charge(request("tok_visa", "order-1-attempt-0"));
        var second = service.charge(request("tok_visa", "order-1-attempt-1"));

        assertEquals(first.paymentId(), second.paymentId());
    }

    @Test
    void keyReusedForDifferentAmountIsRejected() {
        service.charge(request("tok_chargeDeclined", "order-1-attempt-0"));

        ChargeRequest different = new ChargeRequest(1L, 7L, new BigDecimal("1.00"), "USD", "tok_visa", "order-1-attempt-0");
        assertThrows(PaymentConflictException.class, () -> service.charge(different));
    }

    @Test
    void providerOutageLeavesPaymentProcessingForRetry() {
        assertThrows(PaymentProviderException.class, () -> service.charge(request("tok_providerError", "order-1-attempt-0")));

        Payment payment = byKey.get("order-1-attempt-0");
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        verify(events, never()).publish(any(), any());

        // A retry while the first attempt may still be in flight is refused...
        assertThrows(PaymentConflictException.class, () -> service.charge(request("tok_visa", "order-1-attempt-0")));

        // ...but once it is clearly abandoned, the same key is re-driven to a result.
        ReflectionTestUtils.setField(payment, "updatedAt", LocalDateTime.now().minusMinutes(5));
        var retried = service.charge(request("tok_visa", "order-1-attempt-0"));
        assertEquals(PaymentStatus.SUCCEEDED, retried.status());
    }

    @Test
    void refundMovesSucceededPaymentToRefunded() {
        var charged = service.charge(request("tok_visa", "order-1-attempt-0"));

        var refunded = service.refund(charged.paymentId(), "Reservation expired");

        assertEquals(PaymentStatus.REFUNDED, refunded.status());
        assertEquals("Reservation expired", refunded.refundReason());
        verify(events).publish(eq(PaymentEventType.PAYMENT_REFUNDED), any());
        // Refunding twice is a no-op, not an error.
        assertEquals(PaymentStatus.REFUNDED, service.refund(charged.paymentId(), "again").status());
    }

    @Test
    void declinedPaymentCannotBeRefunded() {
        var declined = service.charge(request("tok_chargeDeclined", "order-1-attempt-0"));

        assertThrows(PaymentConflictException.class, () -> service.refund(declined.paymentId(), null));
    }

    private static ChargeRequest request(String token, String key) {
        return new ChargeRequest(1L, 7L, new BigDecimal("999.00"), "USD", token, key);
    }
}
