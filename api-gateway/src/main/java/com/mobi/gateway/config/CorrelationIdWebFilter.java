package com.mobi.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Mints the correlation ID at the edge.
 *
 * <p>The gateway is the only component that sees a request before anything else,
 * so it is the right place to assign the ID: every downstream service then adopts
 * the same value and a single request is greppable across all six logs. The
 * downstream filters still generate their own as a fallback, which covers a
 * service called directly, but in normal traffic the ID originates here.
 *
 * <p>This is a reactive {@link WebFilter}, not a servlet {@code OncePerRequestFilter}
 * — the gateway is WebFlux, where a servlet filter would never run. It also does not
 * populate the MDC: in a reactive pipeline the logging call happens on a different
 * thread than the one that entered the filter, so a plain {@code MDC.put} would
 * attach to the wrong request or leak between them. Correlated logging lives in the
 * downstream servlet services; here the job is to assign and forward.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String existing = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String correlationId = (existing == null || existing.isBlank())
                ? UUID.randomUUID().toString()
                : existing;

        // Mutate the forwarded request so downstream services receive the header,
        // and echo it so a caller can quote the ID when reporting a problem.
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        if (log.isDebugEnabled()) {
            log.debug("Routing {} {} with correlationId={}",
                    mutated.getMethod(), mutated.getURI().getPath(), correlationId);
        }

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // Ahead of the routing filters so the header is present on the proxied request.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
