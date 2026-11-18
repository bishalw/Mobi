package com.mobi.auth.dto;

/** The response body plus the refresh token, which travels separately as a cookie. */
public record LoginResult(AuthResponse response, String refreshToken) {
}
