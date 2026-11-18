package com.mobi.order.service;

import com.mobi.order.entity.CustomerOrder;
import com.mobi.order.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class OrderAuthorizationService {

    public void requireAdmin() {
        if (!isAdmin(currentAuthentication())) {
            throw new AccessDeniedException("Admin access is required");
        }
    }

    public void requireOwnerOrAdmin(Long userId) {
        Authentication authentication = currentAuthentication();
        if (isAdmin(authentication)) {
            return;
        }
        Long currentUserId = currentUserId(authentication);
        if (currentUserId == null || !currentUserId.equals(userId)) {
            throw new AccessDeniedException("You cannot access this user's orders");
        }
    }

    public void requireOwnerOrAdmin(CustomerOrder order) {
        requireOwnerOrAdmin(order.getUserId());
    }

    private Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return authentication;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
    }

    private Long currentUserId(Authentication authentication) {
        return CurrentUser.from(authentication).map(CurrentUser::userId).orElse(null);
    }
}
