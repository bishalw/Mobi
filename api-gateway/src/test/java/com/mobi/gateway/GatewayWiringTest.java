package com.mobi.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Boots the real gateway context. Redis is not needed to start (connections are lazy),
 * so this catches wiring mistakes such as ambiguous rate limiter or key resolver beans
 * without any infrastructure.
 */
@SpringBootTest(properties = {
        "gateway.jwt.issuer=" + GatewayWiringTest.ISSUER,
        "management.tracing.export.otlp.enabled=false"
})
class GatewayWiringTest {

    static final String ISSUER = "http://auth-service:8082";
    static final RSAKey AUTH_KEY;
    static final HttpServer JWKS;

    // A stand-in for auth-service's /.well-known/jwks.json.
    static {
        try {
            AUTH_KEY = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
            byte[] body = new JWKSet(AUTH_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            JWKS.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("gateway.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/.well-known/jwks.json");
    }

    @AfterAll
    static void stopJwks() {
        JWKS.stop(0);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private KeyResolver userOrIpKeyResolver;

    @Test
    void allRoutesAreRegisteredWithCredentialRouteFirst() {
        List<String> ids = routeLocator.getRoutes().map(Route::getId).collectList().block();
        assertEquals(List.of("auth-credentials", "auth-service", "product-service", "inventory-service",
                "order-service", "payment-service", "procurement-service"), ids);
    }

    @Test
    void internalPathsAreNotReachableThroughTheGateway() {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
        client.post().uri("/paymentapi/internal/payments").exchange().expectStatus().isNotFound();
        client.post().uri("/paymentapi/INTERNAL/payments/x/refund").exchange().expectStatus().isNotFound();
    }

    @Test
    void signedInRequestsAreLimitedPerUserAndOthersPerEdgeIp() {
        String token = token(ISSUER, "alice", 42L);

        assertEquals("user:42", key(MockServerHttpRequest.get("/orderapi/orders")
                .header("Authorization", "Bearer " + token).build()));

        // The right-most hop is the one our Envoy appended; anything left of it is client-supplied.
        assertEquals("ip:203.0.113.9", key(MockServerHttpRequest.get("/productapi/products")
                .header("X-Forwarded-For", "1.2.3.4, 203.0.113.9").build()));

        // A forged token does not get its own bucket.
        assertEquals("ip:203.0.113.9", key(MockServerHttpRequest.get("/orderapi/orders")
                .header("Authorization", "Bearer forged.token.value")
                .header("X-Forwarded-For", "203.0.113.9").build()));

        // Validly signed but from another issuer: also anonymous.
        assertEquals("ip:203.0.113.9", key(MockServerHttpRequest.get("/orderapi/orders")
                .header("Authorization", "Bearer " + token("http://evil.example", "mallory", 666L))
                .header("X-Forwarded-For", "203.0.113.9").build()));
    }

    private String key(MockServerHttpRequest request) {
        return userOrIpKeyResolver.resolve(MockServerWebExchange.from(request)).block();
    }


    private static String token(String issuer, String subject, Long userId) {
        try {
            Instant now = Instant.now();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(AUTH_KEY.getKeyID()).build(),
                    new JWTClaimsSet.Builder().issuer(issuer).audience("mobi-api").subject(subject)
                            .claim("userId", userId).issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plusSeconds(300))).build());
            jwt.sign(new RSASSASigner(AUTH_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
