package com.mobi.gateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Routes and their rate limits. Defined in Java rather than properties because each
 * route needs a specific limiter and key resolver bean, which reads far more clearly
 * as code than as indexed property keys.
 *
 * <p>Limits are token buckets in Redis: {@code replenishRate} tokens per second up to
 * {@code burstCapacity}, each request costing {@code requestedTokens}. Over the limit
 * the gateway answers 429 with X-RateLimit-* headers. If Redis is unreachable the
 * limiter fails open (requests pass), trading protection for availability.
 */
@Configuration
public class RoutesConfig {

    /** General API traffic: 20 requests/second sustained per user or IP, bursts of 40. */
    @Bean
    @Primary
    public RedisRateLimiter apiRateLimiter(
            @Value("${ratelimit.api.replenish-rate:20}") int replenishRate,
            @Value("${ratelimit.api.burst-capacity:40}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }

    /**
     * Login, registration and token refresh: 10 requests a minute per IP, with a burst
     * of 10. Each request costs 6 tokens against a refill of 1 per second. This is what
     * makes password spraying across many accounts slow, which the per-account lockout
     * in auth-service cannot do on its own.
     */
    @Bean
    public RedisRateLimiter credentialRateLimiter(
            @Value("${ratelimit.credentials.per-minute:10}") int perMinute) {
        int cost = Math.max(1, 60 / perMinute);
        return new RedisRateLimiter(1, cost * perMinute, cost);
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                               RedisRateLimiter apiRateLimiter,
                               @Qualifier("credentialRateLimiter") RedisRateLimiter credentialRateLimiter,
                               KeyResolver userOrIpKeyResolver,
                               @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
                               @Value("${services.auth.url}") String authUrl,
                               @Value("${services.product.url}") String productUrl,
                               @Value("${services.inventory.url}") String inventoryUrl,
                               @Value("${services.order.url}") String orderUrl,
                               @Value("${services.payment.url}") String paymentUrl,
                               @Value("${services.procurement.url}") String procurementUrl) {
        return builder.routes()
                // Listed before the general auth route; the first match wins.
                .route("auth-credentials", r -> r
                        .path("/auth/login", "/auth/register", "/auth/refresh")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(credentialRateLimiter)
                                .setKeyResolver(ipKeyResolver)))
                        .uri(authUrl))
                .route("auth-service", r -> r.path("/auth/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter).setKeyResolver(userOrIpKeyResolver)))
                        .uri(authUrl))
                .route("product-service", r -> r.path("/productapi/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter).setKeyResolver(userOrIpKeyResolver)))
                        .uri(productUrl))
                .route("inventory-service", r -> r.path("/inventoryapi/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter).setKeyResolver(userOrIpKeyResolver)))
                        .uri(inventoryUrl))
                .route("order-service", r -> r.path("/orderapi/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter).setKeyResolver(userOrIpKeyResolver)))
                        .uri(orderUrl))
                .route("payment-service", r -> r.path("/paymentapi/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter).setKeyResolver(userOrIpKeyResolver)))
                        .uri(paymentUrl))
                .route("procurement-service", r -> r.path("/procurementapi/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter).setKeyResolver(userOrIpKeyResolver)))
                        .uri(procurementUrl))
                .build();
    }
}
