package com.scalablerest.order.security;

public record CurrentUser(Long userId, String username, String role) {
}
