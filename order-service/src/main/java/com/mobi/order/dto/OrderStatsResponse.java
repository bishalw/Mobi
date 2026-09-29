package com.mobi.order.dto;

import java.math.BigDecimal;

// Totals across every order, computed in SQL.
//
// The dashboard used to derive these in the browser by downloading all orders, which meant
// the numbers were only correct while the whole table fitted in one response.
public class OrderStatsResponse {

    private long totalOrders;
    private long confirmedOrders;
    private long pendingOrders;
    private BigDecimal totalRevenue;
    private long distinctCustomers;

    public OrderStatsResponse() {
    }

    public OrderStatsResponse(long totalOrders, long confirmedOrders, long pendingOrders,
                              BigDecimal totalRevenue, long distinctCustomers) {
        this.totalOrders = totalOrders;
        this.confirmedOrders = confirmedOrders;
        this.pendingOrders = pendingOrders;
        this.totalRevenue = totalRevenue;
        this.distinctCustomers = distinctCustomers;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public long getConfirmedOrders() {
        return confirmedOrders;
    }

    public void setConfirmedOrders(long confirmedOrders) {
        this.confirmedOrders = confirmedOrders;
    }

    public long getPendingOrders() {
        return pendingOrders;
    }

    public void setPendingOrders(long pendingOrders) {
        this.pendingOrders = pendingOrders;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getDistinctCustomers() {
        return distinctCustomers;
    }

    public void setDistinctCustomers(long distinctCustomers) {
        this.distinctCustomers = distinctCustomers;
    }
}
