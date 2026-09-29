package com.mobi.inventory.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts the inbound correlation ID into the logging MDC so every log line emitted
 * while handling this request carries it. The api-gateway mints the ID at the
 * edge; this filter only adopts it, and falls back to a fresh one so a service
 * called directly is still traceable.
 *
 * <p>Deliberately duplicated per service rather than extracted to a shared
 * library: each service is an independently built Maven project whose Dockerfile
 * copies only its own directory, so a shared module would require a parent
 * aggregator and break the image builds. Same reasoning as JwtUtil and
 * ErrorResponse, which are duplicated for the same reason.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
