package com.mobi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SHA-256 hex of the raw token. The raw value only ever exists in the cookie.
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 36)
    private String familyId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime revokedAt;

    // Set when this token was exchanged for its successor. A replaced token that comes
    // back is the signal of theft.
    @Column
    private LocalDateTime replacedAt;

    @Column(length = 255)
    private String userAgent;

    protected RefreshToken() {
    }

    public RefreshToken(String tokenHash, Long userId, String familyId, LocalDateTime expiresAt, String userAgent) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255));
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isUsable(LocalDateTime now) {
        return revokedAt == null && replacedAt == null && expiresAt.isAfter(now);
    }

    public void markReplaced(LocalDateTime now) {
        replacedAt = now;
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public Long getUserId() { return userId; }
    public String getFamilyId() { return familyId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public LocalDateTime getReplacedAt() { return replacedAt; }
}
