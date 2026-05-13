package com.scalablerest.order.event;

import com.scalablerest.order.entity.CustomerOrder;
import com.scalablerest.order.entity.OrderItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final String orderEventsTopic;

    public OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafkaTemplate,
                               @Value("${orders.kafka.topic}") String orderEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
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

        kafkaTemplate.send(orderEventsTopic, String.valueOf(order.getId()), event);
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
