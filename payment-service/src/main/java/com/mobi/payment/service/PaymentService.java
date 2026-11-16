package com.mobi.payment.service;

import com.mobi.payment.dto.ChargeRequest;
import com.mobi.payment.dto.PageResponse;
import com.mobi.payment.dto.PaymentResponse;
import com.mobi.payment.entity.Payment;
import com.mobi.payment.entity.PaymentStatus;
import com.mobi.payment.event.PaymentEventPublisher;
import com.mobi.payment.event.PaymentEventType;
import com.mobi.payment.exception.PaymentConflictException;
import com.mobi.payment.exception.PaymentNotFoundException;
import com.mobi.payment.gateway.PaymentProvider;
import com.mobi.payment.gateway.PaymentProvider.ChargeCommand;
import com.mobi.payment.gateway.PaymentProvider.ChargeOutcome;
import com.mobi.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Charges and refunds, idempotent on the caller's key.
 *
 * <p>A charge happens in three steps, each committed on its own:
 * <ol>
 *   <li>insert a PROCESSING row, whose unique idempotency key is the lock that stops
 *       two concurrent requests from both reaching the provider;</li>
 *   <li>call the provider, outside any transaction, through the circuit breaker;</li>
 *   <li>record the outcome together with its outbox event.</li>
 * </ol>
 * If step 2 gets no answer the row stays PROCESSING. A retry with the same key then
 * asks the provider again, which is safe because the provider is idempotent on the
 * same key, and resolves the row either way.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProvider provider;
    private final CircuitBreaker circuitBreaker;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentAuthorization authorization;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final Duration processingTimeout;

    public PaymentService(PaymentRepository paymentRepository, PaymentProvider provider,
                          CircuitBreaker paymentProviderCircuitBreaker, PaymentEventPublisher eventPublisher,
                          PaymentAuthorization authorization, TransactionTemplate transactionTemplate,
                          MeterRegistry meterRegistry,
                          @Value("${payments.processing-timeout-ms:30000}") long processingTimeoutMs) {
        this.paymentRepository = paymentRepository;
        this.provider = provider;
        this.circuitBreaker = paymentProviderCircuitBreaker;
        this.eventPublisher = eventPublisher;
        this.authorization = authorization;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
        this.processingTimeout = Duration.ofMillis(processingTimeoutMs);
    }

    public PaymentResponse charge(ChargeRequest request) {
        // One successful payment per order, whatever key it arrives with. Without this a
        // client bug that minted a fresh key per retry could charge the same order twice.
        var alreadyPaid = paymentRepository.findFirstByOrderIdAndStatus(request.orderId(), PaymentStatus.SUCCEEDED);
        if (alreadyPaid.isPresent()) {
            return PaymentResponse.from(alreadyPaid.get());
        }

        Payment payment = claim(request);
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            return PaymentResponse.from(payment);
        }

        ChargeOutcome outcome = circuitBreaker.executeSupplier(() -> provider.charge(new ChargeCommand(
                request.amount(), request.currency(), request.paymentMethodToken(), request.idempotencyKey(),
                "Mobi order " + request.orderId())));

        Payment resolved = transactionTemplate.execute(status -> {
            Payment current = paymentRepository.findByPaymentId(payment.getPaymentId()).orElseThrow();
            if (current.getStatus() != PaymentStatus.PROCESSING) {
                return current;
            }
            if (outcome.approved()) {
                current.markSucceeded(outcome.providerReference(), outcome.cardBrand(), outcome.cardLast4());
                eventPublisher.publish(PaymentEventType.PAYMENT_SUCCEEDED, current);
            } else {
                current.markDeclined(outcome.providerReference(), outcome.failureCode(), outcome.failureMessage());
                eventPublisher.publish(PaymentEventType.PAYMENT_DECLINED, current);
            }
            return paymentRepository.save(current);
        });
        meterRegistry.counter("payments.charges", "outcome", resolved.getStatus().name().toLowerCase(),
                "provider", provider.name()).increment();
        log.info("Payment {} for order {} {}", resolved.getPaymentId(), resolved.getOrderId(), resolved.getStatus());
        return PaymentResponse.from(resolved);
    }

    /**
     * Returns the payment that owns this idempotency key, creating it if needed. A
     * PROCESSING row is only returned for re-driving when it has been abandoned: a
     * request still in flight gets a 409 instead of a duplicate provider call.
     */
    private Payment claim(ChargeRequest request) {
        var existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return reuse(existing.get(), request);
        }
        try {
            return transactionTemplate.execute(status -> {
                Payment payment = new Payment();
                payment.setIdempotencyKey(request.idempotencyKey());
                payment.setOrderId(request.orderId());
                payment.setUserId(request.userId());
                payment.setAmount(request.amount());
                payment.setCurrency(request.currency());
                payment.setProvider(provider.name());
                return paymentRepository.saveAndFlush(payment);
            });
        } catch (DataIntegrityViolationException raced) {
            // A concurrent request with the same key inserted first.
            Payment winner = paymentRepository.findByIdempotencyKey(request.idempotencyKey()).orElseThrow(() -> raced);
            return reuse(winner, request);
        }
    }

    private Payment reuse(Payment existing, ChargeRequest request) {
        if (!Objects.equals(existing.getOrderId(), request.orderId())
                || existing.getAmount().compareTo(request.amount()) != 0) {
            throw new PaymentConflictException("Idempotency key was already used for a different payment");
        }
        if (existing.getStatus() == PaymentStatus.PROCESSING
                && existing.getUpdatedAt().isAfter(LocalDateTime.now().minus(processingTimeout))) {
            throw new PaymentConflictException("A payment for this order is already in progress");
        }
        return existing;
    }

    public PaymentResponse refund(String paymentId, String reason) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new PaymentConflictException("Only a succeeded payment can be refunded, this one is " + payment.getStatus());
        }

        circuitBreaker.executeSupplier(() -> provider.refund(payment.getProviderReference(), payment.getAmount(),
                payment.getCurrency(), "refund-" + payment.getPaymentId()));

        Payment refunded = transactionTemplate.execute(status -> {
            Payment current = paymentRepository.findByPaymentId(paymentId).orElseThrow();
            if (current.getStatus() == PaymentStatus.REFUNDED) {
                return current;
            }
            current.markRefunded(reason == null || reason.isBlank() ? "Refunded" : reason);
            eventPublisher.publish(PaymentEventType.PAYMENT_REFUNDED, current);
            return paymentRepository.save(current);
        });
        meterRegistry.counter("payments.refunds", "provider", provider.name()).increment();
        log.info("Payment {} for order {} refunded", refunded.getPaymentId(), refunded.getOrderId());
        return PaymentResponse.from(refunded);
    }

    public PaymentResponse get(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        authorization.requireOwnerOrAdmin(payment.getUserId());
        return PaymentResponse.from(payment);
    }

    public List<PaymentResponse> forOrder(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        if (!payments.isEmpty()) {
            authorization.requireOwnerOrAdmin(payments.get(0).getUserId());
        }
        return payments.stream().map(PaymentResponse::from).toList();
    }

    public PageResponse<PaymentResponse> list(int page, int size) {
        return PageResponse.from(paymentRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(PaymentResponse::from));
    }
}
