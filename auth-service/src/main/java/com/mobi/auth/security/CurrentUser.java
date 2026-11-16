package com.mobi.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * The signed-in caller, read from the validated access token's claims.
 * {@code userId} is null for a service token; {@code supplierId} is set only for a
 * supplier account.
 */
public record CurrentUser(Long userId, String username, String role, Long supplierId) {

    public CurrentUser(Long userId, String username, String role) {
        this(userId, username, role, null);
    }

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(asLong(jwt.getClaims().get("userId")), jwt.getSubject(),
                jwt.getClaimAsString("role"), asLong(jwt.getClaims().get(TokenIssuer.SUPPLIER_ID_CLAIM)));
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    public static Optional<CurrentUser> from(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(from(jwt));
        }
        return Optional.empty();
    }
}
