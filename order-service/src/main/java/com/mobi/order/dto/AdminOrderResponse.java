package com.mobi.order.dto;

import com.mobi.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// An order as it appears in the admin list, with the customer's name resolved.
//
// Deliberately carries no line items. A list page shows one row per order, and fetching
// the items for every row would issue a query per order to render data nobody reads —
// GET /orderapi/orders/{id} already returns the full order when someone opens one.
public class AdminOrderResponse {

    private Long id;
    private Long userId;
    private String username;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AdminOrderResponse() {
    }

    public AdminOrderResponse(Long id, Long userId, String username, OrderStatus status,
                              BigDecimal totalAmount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
