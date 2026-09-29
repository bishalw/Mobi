package com.mobi.order.repository;

import com.mobi.order.entity.UserRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// The aggregate query lives in CustomerSummaryQueryRepository: it needs an ORDER BY over
// aggregate expressions, which a derived or @Query method cannot express safely.
@Repository
public interface UserRefRepository extends JpaRepository<UserRef, Long>, CustomerSummaryQueryRepository {
}
