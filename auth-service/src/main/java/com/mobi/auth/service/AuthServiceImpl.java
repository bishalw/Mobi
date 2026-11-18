package com.mobi.auth.service;

import com.mobi.auth.dto.AuthResponse;
import com.mobi.auth.dto.LoginRequest;
import com.mobi.auth.dto.LoginResult;
import com.mobi.auth.dto.RegisterResponse;
import com.mobi.auth.dto.RegisterRequest;
import com.mobi.auth.entity.User;
import com.mobi.auth.event.UserEventPublisher;
import com.mobi.auth.event.UserEventType;
import com.mobi.auth.exception.AccountDisabledException;
import com.mobi.auth.exception.AccountLockedException;
import com.mobi.auth.exception.InvalidCredentialsException;
import com.mobi.auth.exception.InvalidRefreshTokenException;
import com.mobi.auth.repository.UserRepository;
import com.mobi.auth.security.TokenIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final UserEventPublisher userEventPublisher;
    private final RefreshTokenService refreshTokenService;
    private final int maxFailedAttempts;
    private final Duration lockDuration;
    // Compared against when the username does not exist, so an unknown username costs
    // the same bcrypt work as a wrong password and response time does not reveal which
    // usernames are registered.
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           TokenIssuer tokenIssuer, UserEventPublisher userEventPublisher,
                           RefreshTokenService refreshTokenService,
                           @Value("${auth.lockout.max-attempts:5}") int maxFailedAttempts,
                           @Value("${auth.lockout.duration-minutes:15}") long lockMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.userEventPublisher = userEventPublisher;
        this.refreshTokenService = refreshTokenService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
        this.dummyPasswordHash = passwordEncoder.encode("mobi-timing-equaliser");
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
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());

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

    // noRollbackFor: a wrong password throws, and the failed-attempt counter written just
    // before must still commit, or the lockout would never accumulate.
    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public LoginResult login(LoginRequest request, String userAgent) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.getPassword(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        LocalDateTime now = LocalDateTime.now();
        // While locked the password is not even checked, so guessing gains nothing.
        if (user.isLocked(now)) {
            throw new AccountLockedException(Duration.between(now, user.getLockedUntil()));
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            user.recordFailedLogin(maxFailedAttempts, lockDuration, now);
            userRepository.save(user);
            if (user.isLocked(now)) {
                throw new AccountLockedException(lockDuration);
            }
            throw new InvalidCredentialsException();
        }

        // Checked after the password, not before. Reversing these would let anyone
        // discover which usernames exist and are suspended without holding credentials
        // for them, since the two failures return different statuses.
        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }

        user.recordSuccessfulLogin(now);
        userRepository.save(user);

        return new LoginResult(accessResponse(user), refreshTokenService.issue(user.getId(), userAgent));
    }

    /**
     * Swaps a refresh token for a new access token and a new refresh token. The user is
     * re-read every time, so a role change takes effect at the next refresh and a
     * suspension ends the session at the next refresh at the latest, rather than
     * whenever the old token would have expired.
     */
    @Override
    public LoginResult refresh(String refreshToken, String userAgent) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken, userAgent);
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Account no longer exists"));
        if (!user.isEnabled()) {
            refreshTokenService.revokeAllForUser(user.getId());
            throw new InvalidRefreshTokenException("Account is disabled");
        }
        return new LoginResult(accessResponse(user), rotation.token());
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenService.revokeFamilyOf(refreshToken);
    }

    @Override
    public void logoutEverywhere(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    private AuthResponse accessResponse(User user) {
        String token = tokenIssuer.issueUserToken(user.getId(), user.getUsername(), user.getRole(), user.getSupplierId());
        AuthResponse response = new AuthResponse(token, user.getId(), user.getUsername(), user.getRole());
        response.setExpiresIn(tokenIssuer.getExpirationMs() / 1000);
        return response;
    }

    private String normaliseEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
