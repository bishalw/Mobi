package com.mobi.payment.service;

import com.mobi.payment.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PaymentAuthorization {

    public void requireOwnerOrAdmin(Long ownerUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
        if (admin) {
            return;
        }
        if (CurrentUser.from(authentication).map(user -> ownerUserId.equals(user.userId())).orElse(false)) {
            return;
        }
        throw new AccessDeniedException("You cannot access this payment");
    }
}
