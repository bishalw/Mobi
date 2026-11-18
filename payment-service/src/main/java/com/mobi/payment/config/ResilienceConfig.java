package com.mobi.payment.config;

import com.mobi.payment.gateway.PaymentProviderException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    /**
     * Breaker around the external provider. A decline is a normal answer and never
     * counts; only "no answer" (outage, timeout, 5xx) does. While open, charges fail in
     * milliseconds with 503 instead of each one waiting out the provider timeout.
     */
    @Bean
    public CircuitBreaker paymentProviderCircuitBreaker(
            @Value("${resilience.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${resilience.circuit-breaker.slow-call-threshold-ms:5000}") long slowCallThresholdMs,
            @Value("${resilience.circuit-breaker.sliding-window-size:20}") int slidingWindowSize,
            @Value("${resilience.circuit-breaker.minimum-calls:5}") int minimumCalls,
            @Value("${resilience.circuit-breaker.open-state-wait-ms:30000}") long openStateWaitMs,
            MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRateThreshold)
                .slowCallDurationThreshold(Duration.ofMillis(slowCallThresholdMs))
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofMillis(openStateWaitMs))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(PaymentProviderException.class)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry.circuitBreaker("payment-provider");
    }
}
