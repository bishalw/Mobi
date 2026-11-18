package com.mobi.order.config;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * One circuit breaker per downstream service (product, inventory, payment).
 *
 * <p>Only infrastructure failures count against a breaker: connection errors,
 * timeouts and 5xx responses. A 404 for an unknown product or a 409 for
 * insufficient stock is the downstream service working correctly, and counting
 * those would let ordinary customer behaviour trip the breaker and take checkout
 * down for everyone.
 *
 * <p>Timeouts themselves stay with Feign (connect/read timeouts in
 * application.properties) and retries with Feign's {@code Retryer}. The breaker
 * sits outside both, so a call that exhausted its retries counts once.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(
            @Value("${resilience.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${resilience.circuit-breaker.slow-call-threshold-ms:3000}") long slowCallThresholdMs,
            @Value("${resilience.circuit-breaker.sliding-window-size:20}") int slidingWindowSize,
            @Value("${resilience.circuit-breaker.minimum-calls:10}") int minimumCalls,
            @Value("${resilience.circuit-breaker.open-state-wait-ms:15000}") long openStateWaitMs,
            MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRateThreshold)
                .slowCallDurationThreshold(Duration.ofMillis(slowCallThresholdMs))
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofMillis(openStateWaitMs))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(ResilienceConfig::isInfrastructureFailure)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        // Exposes resilience4j_circuitbreaker_state / _calls / _failure_rate on
        // /actuator/prometheus, tagged by breaker name.
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    static boolean isInfrastructureFailure(Throwable throwable) {
        if (throwable instanceof RetryableException) {
            return true;
        }
        if (throwable instanceof FeignException feignException) {
            // status() is -1 when no response was received at all.
            return feignException.status() < 0 || feignException.status() >= 500;
        }
        return false;
    }
}
