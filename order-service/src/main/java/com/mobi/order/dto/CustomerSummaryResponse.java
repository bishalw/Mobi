package com.mobi.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One customer as seen from the orders side: who they are, when they signed up, and what
// they have bought. Assembled by the database, not by the caller.
public class CustomerSummaryResponse {

    private Long userId;
    private String username;
    private String email;
    private boolean enabled;
    private LocalDateTime signedUpAt;
    private long orderCount;
    private BigDecimal totalSpend;
    private LocalDateTime lastOrderAt;

    public CustomerSummaryResponse() {
    }

    public CustomerSummaryResponse(Long userId, String username, String email, boolean enabled,
                                   LocalDateTime signedUpAt, long orderCount, BigDecimal totalSpend,
                                   LocalDateTime lastOrderAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.enabled = enabled;
        this.signedUpAt = signedUpAt;
        this.orderCount = orderCount;
        this.totalSpend = totalSpend;
        this.lastOrderAt = lastOrderAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getSignedUpAt() {
        return signedUpAt;
    }

    public void setSignedUpAt(LocalDateTime signedUpAt) {
        this.signedUpAt = signedUpAt;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(long orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getTotalSpend() {
        return totalSpend;
    }

    public void setTotalSpend(BigDecimal totalSpend) {
        this.totalSpend = totalSpend;
    }

    public LocalDateTime getLastOrderAt() {
        return lastOrderAt;
    }

    public void setLastOrderAt(LocalDateTime lastOrderAt) {
        this.lastOrderAt = lastOrderAt;
    }
}
