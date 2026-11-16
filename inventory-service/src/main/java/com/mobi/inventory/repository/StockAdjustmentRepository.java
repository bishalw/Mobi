package com.mobi.inventory.repository;

import com.mobi.inventory.entity.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    Page<StockAdjustment> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    Optional<StockAdjustment> findByReferenceTypeAndReferenceIdAndProductId(String referenceType, String referenceId, Long productId);
}
