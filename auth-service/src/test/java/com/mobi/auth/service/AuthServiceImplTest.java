package com.mobi.auth.service;

import com.mobi.auth.dto.LoginRequest;
import com.mobi.auth.dto.RegisterRequest;
import com.mobi.auth.exception.AccountLockedException;
import com.mobi.auth.exception.InvalidRefreshTokenException;
import com.mobi.auth.entity.User;
import com.mobi.auth.event.UserEventPublisher;
import com.mobi.auth.event.UserEventType;
import com.mobi.auth.repository.UserRepository;
import com.mobi.auth.exception.InvalidCredentialsException;
import com.mobi.auth.security.TokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private UserRepository userRepository;
    private UserEventPublisher userEventPublisher;
    private PasswordEncoder passwordEncoder;
    private RefreshTokenService refreshTokenService;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        TokenIssuer tokenIssuer = mock(TokenIssuer.class);
        userEventPublisher = mock(UserEventPublisher.class);
        refreshTokenService = mock(RefreshTokenService.class);
        when(tokenIssuer.issueUserToken(any(), any(), any(), any())).thenReturn("access-token");
        when(tokenIssuer.getExpirationMs()).thenReturn(900_000L);
        when(refreshTokenService.issue(any(), any())).thenReturn("refresh-token");
        service = new AuthServiceImpl(userRepository, passwordEncoder, tokenIssuer, userEventPublisher,
                refreshTokenService, 3, 15);

        when(passwordEncoder.encode("Password1!")).thenReturn("hashed");
    }

    @Test
    void registerPublishesUserRegisteredEvent() {
        RegisterRequest request = registerRequest();
        User saved = new User("alice", "hashed", "USER");
        saved.setId(99L);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(saved);

        service.register(request);

        verify(userEventPublisher).publish(UserEventType.USER_REGISTERED, saved);
    }

    @Test
    void registerDoesNotPublishEventForDuplicateUsername() {
        RegisterRequest request = registerRequest();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verify(userRepository, never()).saveAndFlush(any());
        verify(userEventPublisher, never()).publish(any(), any());
    }

    @Test
    void registerReportsConcurrentDuplicateAsConflict() {
        RegisterRequest request = registerRequest();

        // The existence check passes because the competing transaction has not
        // committed yet; the unique constraint is what actually stops the insert.
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry 'alice' for key 'uk_user_data_username'"));

        // Must surface as the same 409 the pre-check produces, not a 500.
        IllegalArgumentException exp =
                assertThrows(IllegalArgumentException.class, () -> service.register(request));
        assertEquals("Username already exists", exp.getMessage());

        // No event may be published for a user that never persisted.
        verify(userEventPublisher, never()).publish(any(), any());
    }

    @Test
    void loginIssuesAccessAndRefreshTokens() {
        User user = existingUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right", "hashed")).thenReturn(true);

        var result = service.login(new LoginRequest("alice", "right"), "test-agent");

        assertEquals("access-token", result.response().getToken());
        assertEquals(900, result.response().getExpiresIn());
        assertEquals("refresh-token", result.refreshToken());
        verify(refreshTokenService).issue(99L, "test-agent");
    }

    @Test
    void repeatedWrongPasswordsLockTheAccount() {
        User user = existingUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThrows(InvalidCredentialsException.class, () -> service.login(new LoginRequest("alice", "wrong"), null));
        assertThrows(InvalidCredentialsException.class, () -> service.login(new LoginRequest("alice", "wrong"), null));
        assertThrows(AccountLockedException.class, () -> service.login(new LoginRequest("alice", "wrong"), null));
        assertTrue(user.isLocked(LocalDateTime.now()));

        // Even the right password is refused while locked, and is not even checked.
        when(passwordEncoder.matches("right", "hashed")).thenReturn(true);
        assertThrows(AccountLockedException.class, () -> service.login(new LoginRequest("alice", "right"), null));
        verify(refreshTokenService, never()).issue(any(), any());
    }

    @Test
    void successfulLoginResetsFailureCount() {
        User user = existingUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right", "hashed")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () -> service.login(new LoginRequest("alice", "wrong"), null));
        service.login(new LoginRequest("alice", "right"), null);

        assertEquals(0, user.getFailedLoginAttempts());
        assertNotNull(user.getLastLoginAt());
    }

    @Test
    void unknownUsernameStillPaysForAPasswordCheck() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> service.login(new LoginRequest("ghost", "x"), null));

        verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("x"), any());
    }

    @Test
    void refreshForDisabledAccountEndsEverySession() {
        User user = existingUser();
        user.setEnabled(false);
        when(refreshTokenService.rotate("old", null)).thenReturn(new RefreshTokenService.Rotation(99L, "new"));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user));

        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("old", null));

        verify(refreshTokenService).revokeAllForUser(99L);
    }

    private User existingUser() {
        User user = new User("alice", "hashed", "USER");
        user.setId(99L);
        return user;
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("Password1!");
        return request;
    }
}
