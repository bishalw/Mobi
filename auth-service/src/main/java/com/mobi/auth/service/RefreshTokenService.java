package com.mobi.auth.service;

import com.mobi.auth.entity.RefreshToken;
import com.mobi.auth.exception.InvalidRefreshTokenException;
import com.mobi.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque, rotating refresh tokens with reuse detection (the scheme in the OAuth 2.0
 * Security BCP, section 4.14).
 *
 * <p>Every refresh replaces the presented token with a new one. If a token that was
 * already replaced is presented again, either the legitimate client or an attacker is
 * holding a stolen copy, and there is no way to tell which, so every token in that
 * login's family is revoked and both have to sign in again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${auth.refresh-token.ttl-days:14}") long ttlDays) {
        this.repository = repository;
        this.ttl = Duration.ofDays(ttlDays);
    }

    public Duration ttl() {
        return ttl;
    }

    /** Starts a new family for a fresh login and returns the raw token. */
    @Transactional
    public String issue(Long userId, String userAgent) {
        return create(userId, UUID.randomUUID().toString(), userAgent);
    }

    public record Rotation(Long userId, String token) {
    }

    /**
     * Exchanges a valid token for a new one in the same family. noRollbackFor matters:
     * the reuse branch revokes the family and then throws, and that revocation must
     * commit, or the stolen family would stay alive.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken, String userAgent) {
        RefreshToken current = find(rawToken);
        LocalDateTime now = LocalDateTime.now();
        if (current.getReplacedAt() != null || current.getRevokedAt() != null) {
            if (current.getReplacedAt() != null) {
                int revoked = repository.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family {}",
                        current.getUserId(), revoked, current.getFamilyId());
            }
            throw new InvalidRefreshTokenException("Session is no longer valid; please sign in again");
        }
        if (!current.isUsable(now)) {
            throw new InvalidRefreshTokenException("Session expired; please sign in again");
        }
        current.markReplaced(now);
        repository.save(current);
        return new Rotation(current.getUserId(), create(current.getUserId(), current.getFamilyId(), userAgent));
    }

    /** Signs out the login this token belongs to. Unknown tokens are ignored. */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), LocalDateTime.now()));
    }

    /** Signs a user out everywhere: on suspension, or "sign out of all devices". */
    @Transactional(propagation = Propagation.REQUIRED)
    public int revokeAllForUser(Long userId) {
        return repository.revokeAllForUser(userId, LocalDateTime.now());
    }

    @Scheduled(cron = "${auth.refresh-token.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void deleteExpired() {
        // A day of grace so a just-expired token is still recognised (and rejected as
        // expired rather than unknown) for anyone debugging a sign-out.
        int deleted = repository.deleteExpiredBefore(LocalDateTime.now().minusDays(1));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }

    private RefreshToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("No session");
        }
        return repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Session is no longer valid; please sign in again"));
    }

    private String create(Long userId, String familyId, String userAgent) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(hash(raw), userId, familyId, LocalDateTime.now().plus(ttl), userAgent));
        return raw;
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exp) {
            throw new IllegalStateException(exp);
        }
    }
}
