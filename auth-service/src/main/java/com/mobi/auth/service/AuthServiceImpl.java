package com.mobi.auth.service;

import com.mobi.auth.dto.AuthResponse;
import com.mobi.auth.dto.LoginRequest;
import com.mobi.auth.dto.RegisterResponse;
import com.mobi.auth.dto.RegisterRequest;
import com.mobi.auth.entity.User;
import com.mobi.auth.event.UserEventPublisher;
import com.mobi.auth.event.UserEventType;
import com.mobi.auth.exception.AccountDisabledException;
import com.mobi.auth.repository.UserRepository;
import com.mobi.auth.security.JwtUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserEventPublisher userEventPublisher;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil, UserEventPublisher userEventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userEventPublisher = userEventPublisher;
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        // Normalised to null when absent or blank. @Email accepts an empty string, and
        // storing "" would let the first two blank submissions collide on the unique
        // email index for no good reason.
        user.setEmail(normaliseEmail(request.getEmail()));

        User savedUser;
        try {
            // saveAndFlush, not save: the unique constraint must be evaluated inside
            // this try block. A plain save() defers the INSERT to commit, which happens
            // after this method returns, so the violation would escape as a 500 instead
            // of the 409 the caller deserves.
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exp) {
            // Lost the race against a concurrent registration of the same username.
            // Report it identically to the check above.
            throw new IllegalArgumentException("Username already exists", exp);
        }

        userEventPublisher.publish(UserEventType.USER_REGISTERED, savedUser);
        return new RegisterResponse(savedUser.getId(), savedUser.getUsername(), "USER REGISTERED");
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Checked after the password, not before. Reversing these would let anyone
        // discover which usernames exist and are suspended without holding credentials
        // for them, since the two failures return different statuses.
        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole());
    }

    private String normaliseEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
