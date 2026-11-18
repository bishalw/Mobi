package com.mobi.notification.listener;

import com.mobi.notification.event.OrderEvent;
import com.mobi.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final NotificationDispatcher dispatcher;

    public OrderEventListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            topics = "${orders.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "orderEventKafkaListenerContainerFactory"
    )
    public void handleOrderEvent(OrderEvent event) {
        if (event == null || event.getEventType() == null) {
            log.warn("Received order event with missing type, skipping. payload={}", event);
            return;
        }
        log.info("Received {} for order {}", event.getEventType(), event.getOrderId());

        Map<String, Object> model = new HashMap<>();
        model.put("orderId", event.getOrderId());
        model.put("total", event.getTotalAmount());
        model.put("items", event.getItems() == null ? List.of() : event.getItems());

        switch (event.getEventType()) {
            case ORDER_INVENTORY_RESERVED -> dispatcher.email(event.getEventId(), event.getUserId(),
                    "order-placed", "We've reserved your order #" + event.getOrderId(), model);
            case ORDER_CONFIRMED -> {
                dispatcher.email(event.getEventId(), event.getUserId(),
                        "order-confirmed", "Order #" + event.getOrderId() + " is confirmed", model);
                dispatcher.sms(event.getEventId(), event.getUserId(), "order-confirmed",
                        "Mobi: order #" + event.getOrderId() + " is confirmed. We'll let you know when it ships.");
            }
            case ORDER_CANCELLED -> dispatcher.email(event.getEventId(), event.getUserId(),
                    "order-cancelled", "Order #" + event.getOrderId() + " was cancelled", model);
        }
    }
}
