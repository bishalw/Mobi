package com.mobi.payment.repository;

import com.mobi.payment.entity.Payment;
import com.mobi.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findFirstByOrderIdAndStatus(Long orderId, PaymentStatus status);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
