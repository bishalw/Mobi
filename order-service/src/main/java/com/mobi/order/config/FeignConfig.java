package com.mobi.order.config;

import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** Registration id of order-service's own client in spring.security.oauth2.client.*. */
    public static final String SERVICE_CLIENT_REGISTRATION = "mobi-services";

    /**
     * Fetches order-service's service token with the client-credentials grant and
     * caches it until shortly before it expires (Spring Security's default skew is
     * 60 seconds), so a checkout normally makes no extra call to auth-service.
     *
     * <p>The service-based manager, rather than the request-based one, because calls
     * also come from schedulers and Kafka listeners with no HTTP request around them.
     */
    @Bean
    public OAuth2AuthorizedClientManager serviceTokenManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    /**
     * Downstream calls go out as order-service, never as the end user.
     *
     * <p>Inventory and payment only check that the caller is a trusted service; who
     * the customer is travels in the request body, after order-service has already
     * authorised them. Forwarding the user's token instead would let the user's
     * permissions decide what a backend may do, and would break any call made after
     * the user's 15-minute token expired, such as a scheduled reservation release.
     */
    @Bean
    public RequestInterceptor serviceTokenInterceptor(OAuth2AuthorizedClientManager serviceTokenManager) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(SERVICE_CLIENT_REGISTRATION)
                .principal("order-service")
                .build();
        return template -> {
            // Throws OAuth2AuthorizationException if auth-service is unreachable or
            // rejects the client; GlobalExceptionHandler turns that into a 503.
            OAuth2AuthorizedClient client = serviceTokenManager.authorize(authorizeRequest);
            if (client == null) {
                throw new IllegalStateException("No service token for " + SERVICE_CLIENT_REGISTRATION);
            }
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
        };
    }

    @Bean
    public RequestInterceptor correlationIdForwardingInterceptor() {
        return template -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            String correlationId = attributes.getRequest().getHeader(CORRELATION_ID_HEADER);
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CORRELATION_ID_HEADER, correlationId);
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
