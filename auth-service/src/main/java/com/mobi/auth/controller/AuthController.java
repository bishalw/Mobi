package com.mobi.auth.controller;

import com.mobi.auth.dto.AuthResponse;
import com.mobi.auth.dto.LoginRequest;
import com.mobi.auth.dto.LoginResult;
import com.mobi.auth.dto.RegisterRequest;
import com.mobi.auth.dto.RegisterResponse;
import com.mobi.auth.exception.InvalidRefreshTokenException;
import com.mobi.auth.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import com.mobi.auth.security.RefreshTokenCookie;
import com.mobi.auth.service.AuthService;
import com.mobi.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshCookie;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenCookie refreshCookie,
                          RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse registerUser(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginUser(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withSession(authService.login(request, http.getHeader(HttpHeaders.USER_AGENT)));
    }

    /** Called by the frontend on page load and shortly before the access token expires. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            HttpServletRequest http) {
        return withSession(authService.refresh(refreshToken, http.getHeader(HttpHeaders.USER_AGENT)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString()).build();
    }

    /** Signs the caller out on every device. */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        if (user.userId() == null) {
            // A service token has no user to sign out.
            throw new AccessDeniedException("A user token is required");
        }
        authService.logoutEverywhere(user.userId());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString()).build();
    }

    // A dead session also clears the cookie, so the browser stops presenting it.
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<com.mobi.auth.exception.ErrorResponse> handleInvalidRefresh(
            InvalidRefreshTokenException exp, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .body(new com.mobi.auth.exception.ErrorResponse(LocalDateTime.now(), HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized", exp.getMessage(), request.getRequestURI(), null));
    }

    private ResponseEntity<AuthResponse> withSession(LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.issue(result.refreshToken(), refreshTokenService.ttl()).toString())
                // Token responses must never be cached by a proxy or the browser.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.response());
    }
}
