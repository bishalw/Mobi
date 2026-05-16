package com.scalablerest.notification.listener;

import com.scalablerest.notification.event.OrderEvent;
import com.scalablerest.notification.event.OrderEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @KafkaListener(
            topics = "${orders.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleOrderEvent(OrderEvent event) {
        if (event == null || event.getEventType() == null) {
            log.warn("Received order event with missing type, skipping. payload={}", event);
            return;
        }

        log.info("Received {} for order {}", event.getEventType(), event.getOrderId());

        OrderEventType type = event.getEventType();
        switch (type) {
            case ORDER_INVENTORY_RESERVED -> log.info(
                    "Sending order placed notification for order {} to user {}",
                    event.getOrderId(), event.getUserId());
            case ORDER_CONFIRMED -> log.info(
                    "Sending order confirmation notification for order {} to user {}",
                    event.getOrderId(), event.getUserId());
            case ORDER_CANCELLED -> log.info(
                    "Sending order cancellation notification for order {} to user {}",
                    event.getOrderId(), event.getUserId());
        }
    }
}
