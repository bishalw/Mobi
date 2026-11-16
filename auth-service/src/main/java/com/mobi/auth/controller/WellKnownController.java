package com.mobi.auth.controller;

import com.mobi.auth.security.SigningKeys;
import com.mobi.auth.security.TokenIssuer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Where other services find out how to verify this service's tokens.
 *
 * <p>{@code /.well-known/jwks.json} is the public key set (RFC 7517). Spring Security
 * on each service fetches it once, caches it, and refetches when a token arrives with
 * a {@code kid} it has not seen, which is what makes key rotation hands-off.
 *
 * <p>{@code /.well-known/oauth-authorization-server} is the RFC 8414 metadata
 * document that points at both. Identity providers like Keycloak publish the same
 * two documents, which is why switching to one is a configuration change.
 */
@RestController
public class WellKnownController {

    private final SigningKeys keys;
    private final TokenIssuer tokenIssuer;

    public WellKnownController(SigningKeys keys, TokenIssuer tokenIssuer) {
        this.keys = keys;
        this.tokenIssuer = tokenIssuer;
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        // toJSONObject() on a JWKSet includes only public parameters by default.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(keys.publicKeys().toJSONObject());
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Map<String, Object> metadata() {
        String issuer = tokenIssuer.issuer();
        return Map.of(
                "issuer", issuer,
                "jwks_uri", issuer + "/.well-known/jwks.json",
                "token_endpoint", issuer + "/oauth2/token",
                "grant_types_supported", List.of("client_credentials"),
                "token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
    }
}
