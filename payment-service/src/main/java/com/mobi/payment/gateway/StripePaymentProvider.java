package com.mobi.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Stripe PaymentIntents, called over plain HTTPS so no SDK is needed. Enabled with
 * {@code payments.provider=stripe} and a secret key in {@code STRIPE_SECRET_KEY};
 * a test-mode key ({@code sk_test_...}) accepts the same {@code tok_*} tokens the
 * simulated provider understands.
 *
 * <p>The idempotency key is sent as Stripe's Idempotency-Key header, so Stripe itself
 * guarantees that a retried charge is not a second charge.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "stripe")
public class StripePaymentProvider implements PaymentProvider {

    // Currencies Stripe expects in whole units rather than cents.
    private static final Set<String> ZERO_DECIMAL = Set.of("JPY", "KRW", "VND", "CLP", "ISK", "UGX", "XAF", "XOF");

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StripePaymentProvider(@Value("${payments.stripe.secret-key}") String secretKey,
                                 @Value("${payments.stripe.base-url:https://api.stripe.com}") String baseUrl,
                                 @Value("${payments.stripe.timeout-ms:8000}") long timeoutMs,
                                 ObservationRegistry observationRegistry) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("payments.provider=stripe requires STRIPE_SECRET_KEY");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 3000)));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .observationRegistry(observationRegistry)
                .build();
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public ChargeOutcome charge(ChargeCommand command) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(toMinorUnits(command.amount(), command.currency())));
        form.add("currency", command.currency().toLowerCase(Locale.ROOT));
        form.add("confirm", "true");
        form.add("description", command.description());
        // Server-side confirmation only: no 3-D Secure redirect to bounce through.
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("automatic_payment_methods[allow_redirects]", "never");
        String token = command.paymentMethodToken();
        if (token.startsWith("pm_")) {
            form.add("payment_method", token);
        } else {
            form.add("payment_method_data[type]", "card");
            form.add("payment_method_data[card][token]", token);
        }

        JsonNode body = post("/v1/payment_intents", form, command.idempotencyKey());
        JsonNode error = body.path("error");
        if (!error.isMissingNode()) {
            String code = error.path("decline_code").asText(error.path("code").asText("card_declined"));
            String reference = error.path("payment_intent").path("id").asText(null);
            return ChargeOutcome.declined(reference, code, error.path("message").asText("Your card was declined."));
        }
        String status = body.path("status").asText();
        String reference = body.path("id").asText();
        if (!"succeeded".equals(status)) {
            return ChargeOutcome.declined(reference, "payment_" + status, "Payment did not complete (" + status + ")");
        }
        JsonNode card = body.path("latest_charge").isObject()
                ? body.path("latest_charge").path("payment_method_details").path("card")
                : objectMapper.missingNode();
        return ChargeOutcome.approved(reference, card.path("brand").asText(null), card.path("last4").asText(null));
    }

    @Override
    public RefundOutcome refund(String providerReference, BigDecimal amount, String currency, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", providerReference);
        form.add("amount", String.valueOf(toMinorUnits(amount, currency)));
        JsonNode body = post("/v1/refunds", form, idempotencyKey);
        if (!body.path("error").isMissingNode()) {
            throw new PaymentProviderException("Stripe refused the refund: " + body.path("error").path("message").asText());
        }
        return new RefundOutcome(body.path("id").asText());
    }

    private JsonNode post(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        try {
            return restClient.post()
                    .uri(uriBuilder -> uriBuilder.path(path).queryParam("expand[]", "latest_charge").build())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        // 402 is a decline and 400 a bad card: definite answers, returned
                        // as a body with an "error" object. 429 and 5xx mean try again.
                        if (status.value() == 429 || status.is5xxServerError()) {
                            throw new PaymentProviderException("Stripe returned " + status.value());
                        }
                        return objectMapper.readTree(response.getBody());
                    });
        } catch (ResourceAccessException exp) {
            throw new PaymentProviderException("Stripe unreachable: " + exp.getMessage(), exp);
        }
    }

    private static long toMinorUnits(BigDecimal amount, String currency) {
        int scale = ZERO_DECIMAL.contains(currency.toUpperCase(Locale.ROOT)) ? 0 : 2;
        return amount.setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).longValueExact();
    }
}
