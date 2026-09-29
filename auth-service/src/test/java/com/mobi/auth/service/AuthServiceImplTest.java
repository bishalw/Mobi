package com.mobi.auth.service;

import com.mobi.auth.dto.RegisterRequest;
import com.mobi.auth.entity.User;
import com.mobi.auth.event.UserEventPublisher;
import com.mobi.auth.event.UserEventType;
import com.mobi.auth.repository.UserRepository;
import com.mobi.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private UserRepository userRepository;
    private UserEventPublisher userEventPublisher;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        userEventPublisher = mock(UserEventPublisher.class);
        service = new AuthServiceImpl(userRepository, passwordEncoder, jwtUtil, userEventPublisher);

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

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("Password1!");
        return request;
    }
}
