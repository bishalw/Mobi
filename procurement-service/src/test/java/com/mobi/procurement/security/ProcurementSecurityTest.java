package com.mobi.procurement.security;

import com.mobi.procurement.config.SecurityConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The real SecurityConfig and the real spring.security.oauth2.resourceserver.*
 * settings from application.properties, against a JWKS endpoint served over HTTP the
 * way auth-service serves it. Proves the role table in SecurityConfig, not just the code.
 */
class ProcurementSecurityTest {

    private static final String ISSUER = "http://auth-service:8082";

    private static RSAKey authKey;
    private static HttpServer jwksServer;
    private static String jwksUri;

    @RestController
    static class Probe {
        @GetMapping({"/procurementapi/purchase-orders", "/procurementapi/purchase-orders/1",
                "/procurementapi/suppliers", "/procurementapi/suppliers/1/products"})
        String read() {
            return "read";
        }

        @GetMapping("/procurementapi/whoami")
        String whoami() {
            return "never reached";
        }

        @PostMapping({"/procurementapi/purchase-orders", "/procurementapi/purchase-orders/1/send",
                "/procurementapi/purchase-orders/1/cancel", "/procurementapi/purchase-orders/1/close",
                "/procurementapi/suppliers"})
        String adminWrite() {
            return "admin";
        }

        @PutMapping({"/procurementapi/purchase-orders/1", "/procurementapi/suppliers/1/products"})
        String adminPut() {
            return "admin";
        }

        @PostMapping({"/procurementapi/purchase-orders/1/accept", "/procurementapi/purchase-orders/1/reject",
                "/procurementapi/purchase-orders/1/shipments"})
        String supplierWrite() {
            return String.valueOf(CurrentUser.from(SecurityContextHolder.getContext().getAuthentication())
                    .map(CurrentUser::supplierId).orElse(null));
        }

        @PostMapping("/procurementapi/purchase-orders/1/receipts")
        String receive() {
            return "received";
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

    private static final String PO = "/procurementapi/purchase-orders";

    @Test
    void anonymousIsUnauthorized() {
        run(mvc -> perform(mvc, get(PO), null, status().isUnauthorized()));
    }

    @Test
    void customerHasNoAccess() {
        String user = token(builder("alice").claim("userId", 7L).claim("role", "USER"), authKey);
        run(mvc -> {
            perform(mvc, get(PO), user, status().isForbidden());
            perform(mvc, get("/procurementapi/suppliers"), user, status().isForbidden());
            perform(mvc, post(PO + "/1/receipts"), user, status().isForbidden());
        });
    }

    @Test
    void warehouseReadsAndReceivesButCannotBuyOrActForTheSupplier() {
        String warehouse = token(builder("warehouse").claim("userId", 2L).claim("role", "WAREHOUSE"), authKey);
        run(mvc -> {
            perform(mvc, get(PO), warehouse, status().isOk());
            perform(mvc, get(PO + "/1"), warehouse, status().isOk());
            perform(mvc, get("/procurementapi/suppliers"), warehouse, status().isOk());
            perform(mvc, post(PO + "/1/receipts"), warehouse, status().isOk());
            perform(mvc, post(PO), warehouse, status().isForbidden());
            perform(mvc, put(PO + "/1"), warehouse, status().isForbidden());
            perform(mvc, post(PO + "/1/send"), warehouse, status().isForbidden());
            perform(mvc, post(PO + "/1/accept"), warehouse, status().isForbidden());
            perform(mvc, post("/procurementapi/suppliers"), warehouse, status().isForbidden());
        });
    }

    @Test
    void supplierActsOnPosAndCarriesItsSupplierId() {
        String supplier = token(builder("supplier").claim("userId", 3L).claim("role", "SUPPLIER")
                .claim("supplier_id", 1L), authKey);
        run(mvc -> {
            perform(mvc, get(PO), supplier, status().isOk());
            perform(mvc, post(PO + "/1/accept"), supplier, status().isOk(), content().string("1"));
            perform(mvc, post(PO + "/1/reject"), supplier, status().isOk());
            perform(mvc, post(PO + "/1/shipments"), supplier, status().isOk());
            perform(mvc, post(PO + "/1/send"), supplier, status().isForbidden());
            perform(mvc, post(PO + "/1/close"), supplier, status().isForbidden());
            perform(mvc, post(PO + "/1/receipts"), supplier, status().isForbidden());
            perform(mvc, get("/procurementapi/suppliers"), supplier, status().isForbidden());
            perform(mvc, get("/procurementapi/suppliers/1/products"), supplier, status().isForbidden());
        });
    }

    @Test
    void adminManagesPosAndSuppliersButCannotActForTheSupplier() {
        String admin = token(builder("root").claim("userId", 1L).claim("role", "ADMIN"), authKey);
        run(mvc -> {
            perform(mvc, post(PO), admin, status().isOk());
            perform(mvc, put(PO + "/1"), admin, status().isOk());
            perform(mvc, post(PO + "/1/send"), admin, status().isOk());
            perform(mvc, post(PO + "/1/cancel"), admin, status().isOk());
            perform(mvc, post(PO + "/1/close"), admin, status().isOk());
            perform(mvc, post(PO + "/1/receipts"), admin, status().isOk());
            perform(mvc, post("/procurementapi/suppliers"), admin, status().isOk());
            perform(mvc, put("/procurementapi/suppliers/1/products"), admin, status().isOk());
            perform(mvc, post(PO + "/1/accept"), admin, status().isForbidden());
            perform(mvc, post(PO + "/1/shipments"), admin, status().isForbidden());
        });
    }

    @Test
    void unmappedPathsAreDenied() {
        String admin = token(builder("root").claim("userId", 1L).claim("role", "ADMIN"), authKey);
        run(mvc -> perform(mvc, get("/procurementapi/whoami"), admin, status().isForbidden()));
    }

    @Test
    void rejectsWrongIssuerAudienceExpiryKeyOrAlgorithm() throws Exception {
        List<String> bad = new ArrayList<>();
        bad.add(token(builder("x").claim("role", "SERVICE").issuer("http://evil.example"), authKey));
        bad.add(token(builder("x").claim("role", "SERVICE").audience("some-other-api"), authKey));
        bad.add(token(builder("x").claim("role", "SERVICE").expirationTime(Date.from(Instant.now().minusSeconds(300))), authKey));
        bad.add(token(builder("x").claim("role", "SERVICE"), new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate()));
        // The old shared-secret style: HS256 must not be accepted at all.
        SignedJWT hs = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder("x").claim("role", "SERVICE").build());
        hs.sign(new MACSigner("an-old-shared-secret-that-is-long-enough!!".getBytes(StandardCharsets.UTF_8)));
        bad.add(hs.serialize());

        run(mvc -> {
            for (String token : bad) {
                perform(mvc, get(PO), token, status().isUnauthorized());
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
