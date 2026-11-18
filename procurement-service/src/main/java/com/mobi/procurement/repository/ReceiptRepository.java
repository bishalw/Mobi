package com.mobi.procurement.repository;

import com.mobi.procurement.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByReceiptKey(String receiptKey);

    List<Receipt> findByPoIdOrderByReceivedAtAscIdAsc(Long poId);
}
