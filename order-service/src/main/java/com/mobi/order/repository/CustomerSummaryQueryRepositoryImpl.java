package com.mobi.order.repository;

import com.mobi.order.dto.CustomerSummaryResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// The ORDER BY is written by hand rather than left to Spring Data.
//
// Spring Data appends sorting to a native query by prefixing the property with the primary
// table alias, so `sort=totalSpend` became `ORDER BY u.totalSpend` — a column that does not
// exist, since totalSpend is a select alias over an aggregate. It silently worked for
// orderCount and failed for totalSpend, which is worse than failing consistently.
//
// Sorting by an aggregate is the entire point of this view, so the sort key is mapped to
// the SQL expression it names, through a fixed whitelist. Nothing from the request reaches
// the SQL string: an unknown key falls back to the default rather than being interpolated.
public class CustomerSummaryQueryRepositoryImpl implements CustomerSummaryQueryRepository {

    private static final String TOTAL_SPEND =
            "COALESCE(SUM(CASE WHEN o.status = 'CONFIRMED' THEN o.total_amount END), 0)";

    private static final Map<String, String> SORTABLE = Map.of(
            "orderCount", "COUNT(o.id)",
            "totalSpend", TOTAL_SPEND,
            "lastOrderAt", "MAX(o.created_at)",
            "signedUpAt", "u.created_at",
            "username", "u.username"
    );

    private static final String DEFAULT_ORDER = "COUNT(o.id) DESC";

    private static final String BASE = """
            SELECT u.user_id,
                   u.username,
                   u.email,
                   u.enabled,
                   u.created_at,
                   COUNT(o.id),
                   %s,
                   MAX(o.created_at)
            FROM user_ref u
            LEFT JOIN orders o ON o.user_id = u.user_id
            WHERE u.username LIKE CONCAT('%%', :search, '%%')
               OR u.email LIKE CONCAT('%%', :search, '%%')
            GROUP BY u.user_id, u.username, u.email, u.enabled, u.created_at
            ORDER BY %s
            """;

    private static final String COUNT = """
            SELECT COUNT(*)
            FROM user_ref u
            WHERE u.username LIKE CONCAT('%', :search, '%')
               OR u.email LIKE CONCAT('%', :search, '%')
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<CustomerSummaryResponse> findCustomerSummaries(String search, Pageable pageable) {
        String term = search == null ? "" : search;

        Query query = entityManager.createNativeQuery(BASE.formatted(TOTAL_SPEND, orderBy(pageable.getSort())));
        query.setParameter("search", term);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<CustomerSummaryResponse> content = rows.stream().map(this::toResponse).toList();

        Query countQuery = entityManager.createNativeQuery(COUNT);
        countQuery.setParameter("search", term);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(content, pageable, total);
    }

    private String orderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return DEFAULT_ORDER;
        }
        StringBuilder clause = new StringBuilder();
        for (Sort.Order order : sort) {
            String expression = SORTABLE.get(order.getProperty());
            if (expression == null) {
                continue;
            }
            if (!clause.isEmpty()) {
                clause.append(", ");
            }
            clause.append(expression).append(order.isAscending() ? " ASC" : " DESC");
        }
        return clause.isEmpty() ? DEFAULT_ORDER : clause.toString();
    }

    private CustomerSummaryResponse toResponse(Object[] row) {
        return new CustomerSummaryResponse(
                toLong(row[0]),
                (String) row[1],
                (String) row[2],
                toBoolean(row[3]),
                toDateTime(row[4]),
                toLong(row[5]) == null ? 0L : toLong(row[5]),
                toDecimal(row[6]),
                toDateTime(row[7])
        );
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.longValue();
        }
        return ((Number) value).longValue();
    }

    // MySQL hands back BIT(1) as a Boolean through some driver paths and as a byte[]
    // through others, so both are handled rather than assuming one.
    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof byte[] bytes) {
            return bytes.length > 0 && bytes[0] != 0;
        }
        return false;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return null;
    }
}
