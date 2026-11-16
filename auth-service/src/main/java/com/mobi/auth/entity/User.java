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
@Table(name = "user_data")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The unique constraint is the real guard against duplicate accounts. The
    // service-level existence check is only a nicety for a better error message —
    // two concurrent registrations both pass it, and duplicate rows would make
    // findByUsername return more than one result, permanently breaking login for
    // that username.
    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    // Optional on purpose. Accounts created before this column existed have no address,
    // and registration does not demand one, so requiring it here would lock out every
    // existing user. The unique index still prevents two accounts claiming the same
    // address once one is supplied.
    @Column(unique = true)
    private String email;

    // E.164, e.g. +15551234567. Optional; only used for SMS notifications.
    @Column(length = 20)
    private String phone;

    // Consecutive failed logins since the last success. Reaching the limit sets
    // lockedUntil, which turns online password guessing from millions of attempts an
    // hour into a handful.
    @Column(nullable = false)
    private int failedLoginAttempts;

    @Column
    private LocalDateTime lockedUntil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastLoginAt;

    // Disabling is the only way to stop an account without deleting it, which matters
    // because orders reference the user id and a deleted row would orphan them.
    @Column(nullable = false)
    private boolean enabled = true;

    // The procurement-service supplier this account acts for. Set iff role is SUPPLIER;
    // it goes into the access token so procurement can scope every query to it.
    @Column(name = "supplier_id")
    private Long supplierId;

    public User() {
    }

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // Stamped here rather than in the service so that every path that creates a user —
    // registration, the bootstrap admin, anything added later — records a signup time
    // without having to remember to.
    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    /** Sets the role, linking or unlinking a supplier so the two can never disagree. */
    public void assignRole(String role, Long supplierId) {
        if ("SUPPLIER".equals(role) && supplierId == null) {
            throw new IllegalArgumentException("A SUPPLIER account needs a supplierId");
        }
        this.role = role;
        this.supplierId = "SUPPLIER".equals(role) ? supplierId : null;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a failed login and locks the account once {@code maxAttempts} is reached. */
    public void recordFailedLogin(int maxAttempts, java.time.Duration lockDuration, LocalDateTime now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
    }

    /** Admin override: lifts a lockout now and clears the failed-attempt count. */
    public void unlock() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void recordSuccessfulLogin(LocalDateTime now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }
}
