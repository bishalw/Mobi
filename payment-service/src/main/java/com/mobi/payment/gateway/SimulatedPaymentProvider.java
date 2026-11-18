package com.mobi.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A stand-in card network for local development and demos. No network, no account.
 *
 * <p>Test tokens follow Stripe's naming so the frontend works unchanged against either
 * provider:
 * <ul>
 *   <li>{@code tok_visa}, {@code tok_mastercard}, {@code tok_amex}: approved</li>
 *   <li>{@code tok_chargeDeclined}: declined, card_declined</li>
 *   <li>{@code tok_chargeDeclinedInsufficientFunds}: declined, insufficient_funds</li>
 *   <li>{@code tok_chargeDeclinedExpiredCard}: declined, expired_card</li>
 *   <li>{@code tok_providerError}: provider outage (exercises retries and the circuit breaker)</li>
 *   <li>{@code tok_slow}: approved after a long delay (exercises timeouts)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentProvider implements PaymentProvider {

    private record Card(String brand, String last4) {
    }

    private static final Map<String, Card> APPROVED = Map.of(
            "tok_visa", new Card("visa", "4242"),
            "tok_mastercard", new Card("mastercard", "4444"),
            "tok_amex", new Card("amex", "0005"),
            "tok_slow", new Card("visa", "4242"));

    private static final Map<String, String[]> DECLINED = Map.of(
            "tok_chargeDeclined", new String[]{"card_declined", "Your card was declined."},
            "tok_chargeDeclinedInsufficientFunds", new String[]{"insufficient_funds", "Your card has insufficient funds."},
            "tok_chargeDeclinedExpiredCard", new String[]{"expired_card", "Your card has expired."});

    private final long latencyMs;
    private final long slowMs;
    // Idempotency, as a real provider does it: the first outcome for a key is replayed.
    private final Map<String, ChargeOutcome> charges = new ConcurrentHashMap<>();
    private final Map<String, RefundOutcome> refunds = new ConcurrentHashMap<>();

    public SimulatedPaymentProvider(@Value("${payments.simulated.latency-ms:150}") long latencyMs,
                                    @Value("${payments.simulated.slow-ms:8000}") long slowMs) {
        this.latencyMs = latencyMs;
        this.slowMs = slowMs;
    }

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public ChargeOutcome charge(ChargeCommand command) {
        ChargeOutcome previous = charges.get(command.idempotencyKey());
        if (previous != null) {
            return previous;
        }
        String token = command.paymentMethodToken();
        if ("tok_providerError".equals(token)) {
            pause(latencyMs);
            throw new PaymentProviderException("Simulated provider outage");
        }
        pause("tok_slow".equals(token) ? slowMs : latencyMs);

        String reference = "sim_ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        ChargeOutcome outcome;
        Card card = APPROVED.get(token);
        if (card != null) {
            outcome = ChargeOutcome.approved(reference, card.brand(), card.last4());
        } else if (DECLINED.containsKey(token)) {
            String[] decline = DECLINED.get(token);
            outcome = ChargeOutcome.declined(reference, decline[0], decline[1]);
        } else {
            outcome = ChargeOutcome.declined(reference, "invalid_payment_method", "The payment method is not recognised.");
        }
        charges.putIfAbsent(command.idempotencyKey(), outcome);
        return charges.get(command.idempotencyKey());
    }

    @Override
    public RefundOutcome refund(String providerReference, BigDecimal amount, String currency, String idempotencyKey) {
        pause(latencyMs);
        return refunds.computeIfAbsent(idempotencyKey,
                key -> new RefundOutcome("sim_re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24)));
    }

    private static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exp) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("Interrupted", exp);
        }
    }
}
