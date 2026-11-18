package com.mobi.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh token's cookie.
 *
 * <ul>
 *   <li>HttpOnly: page JavaScript cannot read it, so an XSS bug cannot steal a
 *       long-lived credential. Only the 15-minute access token lives in memory.</li>
 *   <li>SameSite=Strict: the browser never attaches it to a cross-site request, which
 *       is the CSRF defence for /auth/refresh and /auth/logout.</li>
 *   <li>Path=/auth: sent only to the endpoints that need it, not with every API call.</li>
 *   <li>Secure: on everywhere except plain-http local development.</li>
 * </ul>
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "mobi_refresh";

    private final boolean secure;

    public RefreshTokenCookie(@Value("${auth.refresh-token.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie issue(String token, Duration maxAge) {
        return base(token).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/auth");
    }
}
