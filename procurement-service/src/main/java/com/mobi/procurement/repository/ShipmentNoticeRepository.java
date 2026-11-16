package com.mobi.procurement.repository;

import com.mobi.procurement.entity.ShipmentNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentNoticeRepository extends JpaRepository<ShipmentNotice, Long> {

    List<ShipmentNotice> findByPoIdOrderByCreatedAtAscIdAsc(Long poId);

    List<ShipmentNotice> findByPoIdIn(java.util.Collection<Long> poIds);
}
