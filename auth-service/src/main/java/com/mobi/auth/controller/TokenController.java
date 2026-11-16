package com.mobi.auth.controller;

import com.mobi.auth.security.ServiceClients;
import com.mobi.auth.security.TokenIssuer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * OAuth 2.0 token endpoint, client-credentials grant only (RFC 6749 section 4.4).
 *
 * <p>This is how one service calls another as itself: order-service exchanges its
 * client id and secret for a short-lived service token and sends that as a Bearer
 * token to inventory-service and payment-service. It replaces a shared header
 * secret that every service had to hold and that never expired.
 *
 * <p>Because it follows the standard, the caller uses Spring Security's stock OAuth2
 * client and would work unchanged against Keycloak, Okta or Cognito.
 *
 * <p>Not routed by the api-gateway: only pods inside the cluster can reach it.
 */
@RestController
public class TokenController {

    private final ServiceClients clients;
    private final TokenIssuer tokenIssuer;

    public TokenController(ServiceClients clients, TokenIssuer tokenIssuer) {
        this.clients = clients;
        this.tokenIssuer = tokenIssuer;
    }

    @PostMapping(path = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> form) {
        if (!"client_credentials".equals(form.getFirst("grant_type"))) {
            return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type");
        }

        String[] credentials = basicCredentials(authorization);
        String clientId = credentials != null ? credentials[0] : form.getFirst("client_id");
        String secret = credentials != null ? credentials[1] : form.getFirst("client_secret");
        if (!clients.authenticate(clientId, secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"mobi\"")
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "invalid_client"));
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(Map.of(
                        "access_token", tokenIssuer.issueServiceToken(clientId),
                        "token_type", "Bearer",
                        "expires_in", tokenIssuer.serviceTokenTtlSeconds()));
    }

    /**
     * client_secret_basic: RFC 6749 section 2.3.1 form-encodes id and secret before
     * base64, so both are URL-decoded after splitting.
     */
    static String[] basicCredentials(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) {
                return null;
            }
            return new String[]{
                    URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8),
                    URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8)};
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("error", code));
    }
}
