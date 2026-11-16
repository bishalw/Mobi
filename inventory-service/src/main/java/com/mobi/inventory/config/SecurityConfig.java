package com.mobi.inventory.config;

import com.mobi.inventory.security.SecurityErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    private static final AuthorizationManager<RequestAuthorizationContext> NOT_SUPPLIER = (authentication, context) ->
            new AuthorizationDecision(authentication.get().getAuthorities().stream()
                    .noneMatch(authority -> "SUPPLIER".equals(authority.getAuthority())));

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Who changed stock and why is internal; checked before the public GET below.
                        .requestMatchers(HttpMethod.GET, "/inventoryapi/products/*/adjustments").hasAnyAuthority("ADMIN", "WAREHOUSE")
                        // Public, except to suppliers: stock levels are internal (procurement spec §12).
                        .requestMatchers(HttpMethod.GET, "/inventoryapi/products/**").access(NOT_SUPPLIER)
                        .requestMatchers(HttpMethod.POST, "/inventoryapi/products/*/adjustments").hasAnyAuthority("ADMIN", "WAREHOUSE")
                        .requestMatchers(HttpMethod.PATCH, "/inventoryapi/products/*").hasAnyAuthority("ADMIN", "WAREHOUSE")
                        .requestMatchers(HttpMethod.POST, "/inventoryapi/reservations", "/inventoryapi/reservations/*/release", "/inventoryapi/reservations/*/confirm", "/inventoryapi/reservations/*/return").hasAnyAuthority("ADMIN", "SERVICE")
                        .requestMatchers("/inventoryapi/**").hasAuthority("ADMIN")
                        .anyRequest().authenticated())
                // Bearer tokens are verified by Spring Security against auth-service's public
                // keys; see the spring.security.oauth2.resourceserver.* properties.
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized())
                        .accessDeniedHandler((request, response, exp) -> SecurityErrorResponseWriter.write(
                                request, response, HttpStatus.FORBIDDEN.value(), "Forbidden", exp.getMessage())))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(unauthorized())
                        .accessDeniedHandler((request, response, exp) -> SecurityErrorResponseWriter.write(
                                request, response, HttpStatus.FORBIDDEN.value(), "Forbidden", exp.getMessage())));

        return http.build();
    }

    /** Standard {@code WWW-Authenticate: Bearer ...} header plus the usual JSON error body. */
    private static AuthenticationEntryPoint unauthorized() {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exp) -> {
            bearer.commence(request, response, exp);
            SecurityErrorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized", "Authentication is required");
        };
    }
}
