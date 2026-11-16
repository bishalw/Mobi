package com.mobi.auth.config;

import com.mobi.auth.security.SecurityErrorResponseWriter;
import com.mobi.auth.security.SigningKeys;
import com.mobi.auth.security.TokenIssuer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Collection;

@Configuration
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    // Credential endpoints: no access token needed, and a stale one the browser still
    // holds must not turn a login or refresh into a 401.
    private static final RequestMatcher CREDENTIAL_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.POST, "/auth/register"),
            PATHS.matcher(HttpMethod.POST, "/auth/login"),
            PATHS.matcher(HttpMethod.POST, "/auth/refresh"),
            PATHS.matcher(HttpMethod.POST, "/auth/logout"),
            // Authenticates the calling service itself, from its client id and secret.
            PATHS.matcher(HttpMethod.POST, "/oauth2/token"));

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // The public surface is enumerated one path at a time. This was a blanket
                        // /auth/** permitAll, which is exactly why the admin endpoints sit under
                        // their own prefix: anything added next to them stays closed by default
                        // rather than inheriting a permit from a wildcard higher up the chain.
                        .requestMatchers(CREDENTIAL_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json", "/.well-known/oauth-authorization-server").permitAll()
                        .requestMatchers("/auth/admin/**").hasAuthority("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(unauthorized())
                        .accessDeniedHandler((request, response, exp) -> SecurityErrorResponseWriter.write(
                                request, response, HttpStatus.FORBIDDEN.value(), "Forbidden", exp.getMessage())))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(unauthorized())
                        .accessDeniedHandler((request, response, exp) -> SecurityErrorResponseWriter.write(
                                request, response, HttpStatus.FORBIDDEN.value(), "Forbidden", exp.getMessage())));

        return http.build();
    }

    /**
     * Verifies tokens in-process with this service's own public key: same checks the
     * other services make (signature, expiry, issuer, audience), without fetching our
     * own JWKS over HTTP.
     */
    @Bean
    public JwtDecoder jwtDecoder(SigningKeys keys, TokenIssuer tokenIssuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.currentPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(tokenIssuer.issuer()),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(TokenIssuer.AUDIENCE))));
        return decoder;
    }

    /** The {@code role} claim becomes the authority, unprefixed: ADMIN, USER or SERVICE. */
    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver standard = new DefaultBearerTokenResolver();
        return request -> CREDENTIAL_ENDPOINTS.matches(request) ? null : standard.resolve(request);
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
