package com.mobi.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Services allowed to get a token for themselves with the client-credentials grant,
 * as {@code auth.service-clients.<client-id>=<secret>}.
 *
 * <p>A client with a blank secret is disabled, so a missing environment variable
 * locks that service out instead of letting it in with an empty password.
 */
@ConfigurationProperties(prefix = "auth")
public record ServiceClients(Map<String, String> serviceClients) {

    public ServiceClients {
        serviceClients = serviceClients == null ? Map.of() : Map.copyOf(serviceClients);
    }

    public boolean authenticate(String clientId, String secret) {
        if (clientId == null || secret == null) {
            return false;
        }
        String expected = serviceClients.get(clientId);
        if (expected == null || expected.isBlank()) {
            return false;
        }
        // Constant-time comparison of digests: no early exit that would let a caller
        // learn the secret a byte at a time from response timing.
        return MessageDigest.isEqual(sha256(expected), sha256(secret));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
