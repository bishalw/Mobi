package com.mobi.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Refuses any path with an /internal/ segment, such as payment-service's charge
 * endpoint. Those are for service-to-service calls inside the cluster and already need
 * the internal shared secret; this makes them unreachable from outside even if that
 * secret ever leaked. 404 rather than 403, so their existence is not advertised.
 */
@Component
public class InternalPathBlockingFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath().toLowerCase();
        if (path.contains("/internal/") || path.endsWith("/internal")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
