package com.mobi.order.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Runs a downstream call through that service's circuit breaker.
 *
 * <p>When a breaker is open the call is not attempted and
 * {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException} is thrown
 * immediately; GlobalExceptionHandler maps it to 503. Failing fast is the point:
 * without it every checkout request would hold a servlet thread for the full
 * read timeout while the dependency is down, and order-service would exhaust its
 * own thread pool and fail along with it.
 */
@Component
public class DownstreamCalls {

    public static final String PRODUCT = "product-service";
    public static final String INVENTORY = "inventory-service";
    public static final String PAYMENT = "payment-service";

    private final CircuitBreaker product;
    private final CircuitBreaker inventory;
    private final CircuitBreaker payment;

    public DownstreamCalls(CircuitBreakerRegistry registry) {
        this.product = registry.circuitBreaker(PRODUCT);
        this.inventory = registry.circuitBreaker(INVENTORY);
        this.payment = registry.circuitBreaker(PAYMENT);
    }

    public <T> T product(Supplier<T> call) {
        return product.executeSupplier(call);
    }

    public <T> T inventory(Supplier<T> call) {
        return inventory.executeSupplier(call);
    }

    public void inventory(Runnable call) {
        inventory.executeRunnable(call);
    }

    public <T> T payment(Supplier<T> call) {
        return payment.executeSupplier(call);
    }
}
