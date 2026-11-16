package com.mobi.procurement.config;

import com.mobi.procurement.security.SecurityErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import static com.mobi.procurement.security.CurrentUser.ADMIN;
import static com.mobi.procurement.security.CurrentUser.SUPPLIER;
import static com.mobi.procurement.security.CurrentUser.WAREHOUSE;

/**
 * Role checks per endpoint. Which purchase orders a supplier may see is decided in
 * PurchaseOrderService (own supplier only, else 404), since it depends on the row.
 */
@Configuration
public class SecurityConfig {

    private static final String PO = "/procurementapi/purchase-orders";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/procurementapi/suppliers", "/procurementapi/suppliers/**")
                            .hasAnyAuthority(ADMIN, WAREHOUSE)
                        .requestMatchers("/procurementapi/suppliers", "/procurementapi/suppliers/**").hasAuthority(ADMIN)
                        .requestMatchers(HttpMethod.POST, PO + "/*/accept", PO + "/*/reject", PO + "/*/shipments")
                            .hasAuthority(SUPPLIER)
                        .requestMatchers(HttpMethod.POST, PO + "/*/receipts").hasAnyAuthority(WAREHOUSE, ADMIN)
                        .requestMatchers(HttpMethod.GET, PO, PO + "/**").hasAnyAuthority(ADMIN, WAREHOUSE, SUPPLIER)
                        .requestMatchers(PO, PO + "/**").hasAuthority(ADMIN)
                        .anyRequest().denyAll())
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
