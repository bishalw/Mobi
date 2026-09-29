package com.mobi.auth.security;

public record CurrentUser(Long userId, String username, String role) {
}
