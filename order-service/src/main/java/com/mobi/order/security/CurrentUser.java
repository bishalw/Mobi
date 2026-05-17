package com.mobi.order.security;

public record CurrentUser(Long userId, String username, String role) {
}
