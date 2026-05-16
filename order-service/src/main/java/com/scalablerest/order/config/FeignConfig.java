package com.scalablerest.order.config;

import feign.RequestInterceptor;
import feign.Retryer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Bean
    public RequestInterceptor authorizationHeaderForwardingInterceptor(
            @Value("${internal.service.secret:}") String internalServiceSecret) {
        return template -> {
            if (!internalServiceSecret.isBlank()) {
                template.header("X-Internal-Service-Secret", internalServiceSecret);
            }

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CORRELATION_ID_HEADER, correlationId);
            }
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                template.header("Authorization", authorizationHeader);
            }
        };
    }

    @Bean
    public Retryer retryer(
            @Value("${feign.retry.period-ms:100}") long periodMs,
            @Value("${feign.retry.max-period-ms:500}") long maxPeriodMs,
            @Value("${feign.retry.max-attempts:3}") int maxAttempts) {
        return new Retryer.Default(periodMs, maxPeriodMs, maxAttempts);
    }
}
