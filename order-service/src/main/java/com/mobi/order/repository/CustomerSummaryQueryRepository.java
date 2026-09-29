package com.mobi.order.repository;

import com.mobi.order.dto.CustomerSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerSummaryQueryRepository {

    Page<CustomerSummaryResponse> findCustomerSummaries(String search, Pageable pageable);
}
