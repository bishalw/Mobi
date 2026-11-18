package com.mobi.payment.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * The signed-in caller, read from the validated access token's claims.
 * {@code userId} is null for a service token.
 */
public record CurrentUser(Long userId, String username, String role) {

    public static CurrentUser from(Jwt jwt) {
        Object id = jwt.getClaims().get("userId");
        return new CurrentUser(id instanceof Number n ? n.longValue() : null,
                jwt.getSubject(), jwt.getClaimAsString("role"));
    }

    public static Optional<CurrentUser> from(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(from(jwt));
        }
        return Optional.empty();
    }
}
