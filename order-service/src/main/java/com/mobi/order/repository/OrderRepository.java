package com.mobi.order.repository;

import com.mobi.order.entity.CustomerOrder;
import com.mobi.order.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findByUserId(Long userId);

    // Dashboard totals, each computed by the database over the whole table.
    //
    // Kept as several small queries rather than one aggregate constructor expression: the
    // counts are exact and obvious this way, and an admin dashboard is not a hot path
    // where the extra round trips matter.

    long countByStatus(OrderStatus status);

    long countByStatusIn(Collection<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM CustomerOrder o WHERE o.status = :status")
    BigDecimal sumTotalAmountByStatus(OrderStatus status);

    @Query("SELECT COUNT(DISTINCT o.userId) FROM CustomerOrder o")
    long countDistinctCustomers();
}
