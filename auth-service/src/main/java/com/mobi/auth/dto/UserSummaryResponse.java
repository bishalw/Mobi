package com.mobi.auth.dto;

import com.mobi.auth.entity.User;

import java.time.LocalDateTime;

// The only shape in which a user leaves this service. Built field by field from the
// entity on purpose: returning User directly would put the bcrypt hash on the wire, and
// that mistake is one careless controller signature away.
public class UserSummaryResponse {

    private Long id;
    private String username;
    private String email;
    private String role;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    // Set while the account is locked out after too many wrong passwords; null otherwise.
    private LocalDateTime lockedUntil;
    // The supplier a SUPPLIER account acts for; null for other roles.
    private Long supplierId;

    public UserSummaryResponse() {
    }

    public UserSummaryResponse(Long id, String username, String email, String role,
                               boolean enabled, LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    public static UserSummaryResponse from(User user) {
        UserSummaryResponse response = new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
        response.setLockedUntil(user.isLocked(LocalDateTime.now()) ? user.getLockedUntil() : null);
        response.setSupplierId(user.getSupplierId());
        return response;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
