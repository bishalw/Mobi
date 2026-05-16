package com.scalablerest.inventory.config;

import com.scalablerest.inventory.security.InternalServiceFilter;
import com.scalablerest.inventory.security.JwtFilter;
import com.scalablerest.inventory.security.SecurityErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final InternalServiceFilter internalServiceFilter;

    public SecurityConfig(JwtFilter jwtFilter, InternalServiceFilter internalServiceFilter) {
        this.jwtFilter = jwtFilter;
        this.internalServiceFilter = internalServiceFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/inventoryapi/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/inventoryapi/products").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/inventoryapi/products/*/reserve", "/inventoryapi/products/*/release", "/inventoryapi/products/*/deduct").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/inventoryapi/reservations", "/inventoryapi/reservations/*/release", "/inventoryapi/reservations/*/confirm").hasAnyAuthority("ADMIN", "SERVICE")
                        .requestMatchers("/inventoryapi/**").hasAuthority("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, exp) -> SecurityErrorResponseWriter.write(
                                request, response, HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Authentication is required"))
                        .accessDeniedHandler((request, response, exp) -> SecurityErrorResponseWriter.write(
                                request, response, HttpStatus.FORBIDDEN.value(), "Forbidden", exp.getMessage())))
                .addFilterBefore(internalServiceFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
