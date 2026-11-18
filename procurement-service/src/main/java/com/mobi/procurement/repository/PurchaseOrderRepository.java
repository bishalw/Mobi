package com.mobi.procurement.repository;

import com.mobi.procurement.entity.PurchaseOrder;
import com.mobi.procurement.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /**
     * Every filter is optional; {@code statuses} is never empty (the caller passes all of
     * them). {@code sentOnly} hides POs that never went to the supplier (for supplier callers).
     */
    @Query("""
            select po from PurchaseOrder po
            where po.status in :statuses
              and (:supplierId is null or po.supplierId = :supplierId)
              and (:sentOnly = false or po.sentAt is not null)
            order by po.createdAt desc, po.id desc
            """)
    Page<PurchaseOrder> search(Collection<PurchaseOrderStatus> statuses, Long supplierId, boolean sentOnly, Pageable pageable);
}
