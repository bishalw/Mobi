package com.mobi.order.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobi.order.entity.CustomerOrder;
import com.mobi.order.entity.OrderItem;
import com.mobi.order.outbox.OutboxEvent;
import com.mobi.order.outbox.OutboxEventRepository;
import com.mobi.order.outbox.OutboxTracing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String orderEventsTopic;
    private final OutboxTracing outboxTracing;

    public OrderEventPublisher(OutboxEventRepository outboxEventRepository,
                               @Value("${orders.kafka.topic}") String orderEventsTopic,
                               OutboxTracing outboxTracing) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxTracing = outboxTracing;
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
            OutboxEvent outboxEvent = new OutboxEvent(
                    orderEventsTopic,
                    String.valueOf(order.getId()),
                    String.valueOf(order.getId()),
                    eventType.name(),
                    objectMapper.writeValueAsString(event)
            );
            outboxEvent.setTraceparent(outboxTracing.currentTraceparent());
            outboxEventRepository.save(outboxEvent);
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
