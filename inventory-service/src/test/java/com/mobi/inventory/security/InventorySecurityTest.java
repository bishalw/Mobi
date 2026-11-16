package com.mobi.inventory.security;

import com.mobi.inventory.config.SecurityConfig;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The real SecurityConfig and the real spring.security.oauth2.resourceserver.*
 * settings from application.properties, against a JWKS endpoint served over HTTP the
 * way auth-service serves it. Proves the configuration, not just the code.
 */
class InventorySecurityTest {

    private static final String ISSUER = "http://auth-service:8082";

    private static RSAKey authKey;
    private static HttpServer jwksServer;
    private static String jwksUri;

    @RestController
    static class Probe {
        @GetMapping("/inventoryapi/products/1")
        String stock() {
            return "stock";
        }

        @PostMapping("/inventoryapi/reservations")
        String reserve() {
            return "reserved";
        }

        @PostMapping("/inventoryapi/reservations/42/return")
        String giveBack() {
            return "returned";
        }

        @PostMapping("/inventoryapi/products/1/adjustments")
        String adjust() {
            return "adjusted";
        }

        @GetMapping("/inventoryapi/products/1/adjustments")
        String history() {
            return "history";
        }
    }

    @BeforeAll
    static void startJwks() throws Exception {
        authKey = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        byte[] body = new JWKSet(authKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwksServer.start();
        jwksUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
    }

    @AfterAll
    static void stopJwks() {
        jwksServer.stop(0);
    }

    @Test
    void stockLevelsArePublic() {
        run(mvc -> perform(mvc, get("/inventoryapi/products/1"), null, status().isOk()));
    }

    @Test
    void suppliersCannotSeeStockAtAll() {
        String supplier = token(builder("supplier").claim("userId", 3L).claim("role", "SUPPLIER").claim("supplier_id", 1L), authKey);
        run(mvc -> {
            perform(mvc, get("/inventoryapi/products/1"), supplier, status().isForbidden());
            perform(mvc, get("/inventoryapi/products/1/adjustments"), supplier, status().isForbidden());
            perform(mvc, post("/inventoryapi/products/1/adjustments"), supplier, status().isForbidden());
        });
    }

    @Test
    void onlyServicesAndAdminsReserveStock() {
        String user = token(builder("alice").claim("userId", 7L).claim("role", "USER"), authKey);
        String service = token(builder("order-service").claim("role", "SERVICE"), authKey);
        String admin = token(builder("root").claim("userId", 1L).claim("role", "ADMIN"), authKey);
        run(mvc -> {
            perform(mvc, post("/inventoryapi/reservations"), null, status().isUnauthorized());
            perform(mvc, post("/inventoryapi/reservations"), user, status().isForbidden());
            perform(mvc, post("/inventoryapi/reservations"), service, status().isOk());
            perform(mvc, post("/inventoryapi/reservations"), admin, status().isOk());
            perform(mvc, post("/inventoryapi/reservations/42/return"), service, status().isOk());
            perform(mvc, post("/inventoryapi/reservations/42/return"), user, status().isForbidden());
            // A service token is not an admin token.
            perform(mvc, post("/inventoryapi/products/1/adjustments"), service, status().isForbidden());
        });
    }

    @Test
    void onlyWarehouseAndAdminsAdjustStockOrSeeItsHistory() {
        String user = token(builder("alice").claim("userId", 7L).claim("role", "USER"), authKey);
        String supplier = token(builder("acme").claim("userId", 8L).claim("role", "SUPPLIER").claim("supplier_id", 1L), authKey);
        String warehouse = token(builder("warehouse").claim("userId", 9L).claim("role", "WAREHOUSE"), authKey);
        String admin = token(builder("root").claim("userId", 1L).claim("role", "ADMIN"), authKey);
        run(mvc -> {
            perform(mvc, post("/inventoryapi/products/1/adjustments"), warehouse, status().isOk());
            perform(mvc, post("/inventoryapi/products/1/adjustments"), admin, status().isOk());
            perform(mvc, post("/inventoryapi/products/1/adjustments"), user, status().isForbidden());
            perform(mvc, post("/inventoryapi/products/1/adjustments"), supplier, status().isForbidden());
            perform(mvc, get("/inventoryapi/products/1/adjustments"), warehouse, status().isOk());
            // The ledger is not public, unlike the stock level itself.
            perform(mvc, get("/inventoryapi/products/1/adjustments"), null, status().isUnauthorized());
            perform(mvc, get("/inventoryapi/products/1/adjustments"), supplier, status().isForbidden());
            // Warehouse staff do not get checkout powers.
            perform(mvc, post("/inventoryapi/reservations"), warehouse, status().isForbidden());
        });
    }

    @Test
    void rejectsForeignOrExpiredTokens() throws Exception {
        List<String> bad = new ArrayList<>();
        bad.add(token(builder("x").claim("role", "SERVICE").issuer("http://evil.example"), authKey));
        bad.add(token(builder("x").claim("role", "SERVICE").expirationTime(Date.from(Instant.now().minusSeconds(300))), authKey));
        bad.add(token(builder("x").claim("role", "SERVICE"), new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate()));
        run(mvc -> {
            for (String token : bad) {
                perform(mvc, post("/inventoryapi/reservations"), token, status().isUnauthorized());
            }
        });
    }

    private void run(Consumer<MockMvc> assertions) {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
                        OAuth2ResourceServerAutoConfiguration.class, WebMvcAutoConfiguration.class,
                        DispatcherServletAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                        JacksonAutoConfiguration.class))
                .withUserConfiguration(SecurityConfig.class, Probe.class)
                .withPropertyValues(resourceServerProperties())
                .withPropertyValues("JWT_ISSUER=" + ISSUER, "JWT_JWK_SET_URI=" + jwksUri)
                .run(context -> assertions.accept(webAppContextSetup(context).apply(springSecurity()).build()));
    }

    /** The resource-server lines exactly as they appear in application.properties. */
    private static String[] resourceServerProperties() {
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith("spring.security.oauth2.resourceserver."))
                    .toArray(String[]::new);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JWTClaimsSet.Builder builder(String subject) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience("mobi-api")
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private static String token(JWTClaimsSet.Builder claims, RSAKey key) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void perform(MockMvc mvc, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                String token, ResultMatcher... expectations) {
        try {
            if (token != null) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            var result = mvc.perform(request);
            for (ResultMatcher expectation : expectations) {
                result.andExpect(expectation);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
