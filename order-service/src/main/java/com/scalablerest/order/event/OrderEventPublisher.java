package com.scalablerest.order.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scalablerest.order.entity.CustomerOrder;
import com.scalablerest.order.entity.OrderItem;
import com.scalablerest.order.outbox.OutboxEvent;
import com.scalablerest.order.outbox.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String orderEventsTopic;

    public OrderEventPublisher(OutboxEventRepository outboxEventRepository,
                               @Value("${orders.kafka.topic}") String orderEventsTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.orderEventsTopic = orderEventsTopic;
    }

    public void publish(OrderEventType eventType, CustomerOrder order) {
        OrderEvent event = new OrderEvent(
                eventType,
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                mapItems(order.getItems())
        );

        try {
            outboxEventRepository.save(new OutboxEvent(
                    orderEventsTopic,
                    String.valueOf(order.getId()),
                    String.valueOf(order.getId()),
                    eventType.name(),
                    objectMapper.writeValueAsString(event)
            ));
        } catch (JsonProcessingException exp) {
            throw new IllegalStateException("Unable to serialize order event", exp);
        }
    }

    private List<OrderItemEvent> mapItems(List<OrderItem> items) {
        return items.stream()
                .map(item -> new OrderItemEvent(
                        item.getProductId(),
                        item.getProductNameSnapshot(),
                        item.getUnitPriceSnapshot(),
                        item.getQuantity(),
                        item.getLineTotal()
                ))
                .toList();
    }
}
