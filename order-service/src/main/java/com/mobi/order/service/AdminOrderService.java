package com.mobi.order.service;

import com.mobi.order.dto.AdminOrderResponse;
import com.mobi.order.dto.CustomerSummaryResponse;
import com.mobi.order.dto.OrderStatsResponse;
import com.mobi.order.dto.PageResponse;
import com.mobi.order.entity.CustomerOrder;
import com.mobi.order.entity.UserRef;
import com.mobi.order.enums.OrderStatus;
import com.mobi.order.repository.OrderRepository;
import com.mobi.order.repository.UserRefRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminOrderService {

    private static final Set<OrderStatus> IN_FLIGHT =
            Set.of(OrderStatus.PENDING, OrderStatus.INVENTORY_RESERVED);

    private final OrderRepository orderRepository;
    private final UserRefRepository userRefRepository;
    private final OrderAuthorizationService authorizationService;

    public AdminOrderService(OrderRepository orderRepository, UserRefRepository userRefRepository,
                             OrderAuthorizationService authorizationService) {
        this.orderRepository = orderRepository;
        this.userRefRepository = userRefRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public OrderStatsResponse getStats() {
        authorizationService.requireAdmin();
        return new OrderStatsResponse(
                orderRepository.count(),
                orderRepository.countByStatus(OrderStatus.CONFIRMED),
                orderRepository.countByStatusIn(IN_FLIGHT),
                orderRepository.sumTotalAmountByStatus(OrderStatus.CONFIRMED),
                orderRepository.countDistinctCustomers());
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerSummaryResponse> listCustomers(String search, Pageable pageable) {
        authorizationService.requireAdmin();
        String term = search == null ? "" : search.trim();
        Page<CustomerSummaryResponse> page = userRefRepository.findCustomerSummaries(term, pageable);
        return PageResponse.of(page, page.getContent());
    }

    // Pages the orders, then resolves the names for just that page in a single extra
    // query. Bounded either way: one page of orders and at most one lookup per distinct
    // customer on it, regardless of how large the order table grows.
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> listOrders(Pageable pageable) {
        authorizationService.requireAdmin();
        Page<CustomerOrder> page = orderRepository.findAll(pageable);

        Set<Long> userIds = page.getContent().stream()
                .map(CustomerOrder::getUserId)
                .collect(Collectors.toSet());

        Map<Long, String> usernames = userRefRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserRef::getUserId, UserRef::getUsername));

        List<AdminOrderResponse> content = page.getContent().stream()
                .map(order -> new AdminOrderResponse(
                        order.getId(),
                        order.getUserId(),
                        // Null when the projection has not caught up with a brand new
                        // signup, or when the account predates the topic and no replay has
                        // run. The order is still shown; only the name is missing.
                        usernames.get(order.getUserId()),
                        order.getStatus(),
                        order.getTotalAmount(),
                        order.getCreatedAt(),
                        order.getUpdatedAt()))
                .toList();

        return PageResponse.of(page, content);
    }
}
