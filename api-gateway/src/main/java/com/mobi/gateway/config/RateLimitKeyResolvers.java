package com.mobi.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Collection;

/**
 * Who a request counts against.
 *
 * <p>Signed-in traffic is limited per user, so customers behind one office NAT do not
 * share a budget; anonymous traffic per client IP. The IP is the right-most
 * X-Forwarded-For entry, the one appended by our own Envoy edge proxy. Entries to its
 * left come from the client and can be anything, so trusting them would let a caller
 * pick a fresh bucket per request.
 */
@Configuration
public class RateLimitKeyResolvers {

    static final String AUDIENCE = "mobi-api";

    /**
     * Same verification as the services: RS256 signature against auth-service's JWKS
     * (cached, refetched on an unknown kid), expiry, issuer and audience.
     */
    @Bean
    public ReactiveJwtDecoder rateLimitJwtDecoder(@Value("${gateway.jwt.jwk-set-uri}") String jwkSetUri,
                                                  @Value("${gateway.jwt.issuer}") String issuer) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(AUDIENCE))));
        return decoder;
    }

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver(ReactiveJwtDecoder rateLimitJwtDecoder) {
        return exchange -> userId(exchange.getRequest(), rateLimitJwtDecoder)
                .map(userId -> "user:" + userId)
                .defaultIfEmpty("ip:" + clientIp(exchange.getRequest()));
    }

    /** For credential endpoints, where there is no user yet and the IP is what matters. */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange.getRequest()));
    }

    static Mono<String> userId(ServerHttpRequest request, ReactiveJwtDecoder decoder) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return Mono.empty();
        }
        return decoder.decode(header.substring(7))
                .mapNotNull(RateLimitKeyResolvers::userIdOf)
                // Expired or forged: limited as anonymous, and the service will reject it anyway.
                .onErrorResume(invalid -> Mono.empty());
    }

    private static String userIdOf(Jwt jwt) {
        Object userId = jwt.getClaims().get("userId");
        return userId == null ? jwt.getSubject() : userId.toString();
    }

    static String clientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
