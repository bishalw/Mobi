package com.mobi.procurement.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * The signed-in caller, read from the validated access token's claims.
 * {@code supplierId} is set only for a supplier account, and scopes it to that
 * supplier's purchase orders.
 */
public record CurrentUser(Long userId, String username, String role, Long supplierId) {

    public static final String ADMIN = "ADMIN";
    public static final String WAREHOUSE = "WAREHOUSE";
    public static final String SUPPLIER = "SUPPLIER";
    static final String SUPPLIER_ID_CLAIM = "supplier_id";

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(asLong(jwt.getClaims().get("userId")), jwt.getSubject(),
                jwt.getClaimAsString("role"), asLong(jwt.getClaims().get(SUPPLIER_ID_CLAIM)));
    }

    public static Optional<CurrentUser> from(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(from(jwt));
        }
        return Optional.empty();
    }

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }

    public boolean isWarehouse() {
        return WAREHOUSE.equals(role);
    }

    public boolean isSupplier() {
        return SUPPLIER.equals(role);
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
