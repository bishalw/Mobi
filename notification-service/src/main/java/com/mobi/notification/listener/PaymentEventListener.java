package com.mobi.notification.listener;

import com.mobi.notification.event.PaymentEvent;
import com.mobi.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final NotificationDispatcher dispatcher;

    public PaymentEventListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            topics = "${payments.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "paymentEventKafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(PaymentEvent event) {
        if (event == null || event.eventType() == null) {
            log.warn("Received payment event with missing type, skipping. payload={}", event);
            return;
        }
        log.info("Received {} for order {}", event.eventType(), event.orderId());

        Map<String, Object> model = new HashMap<>();
        model.put("orderId", event.orderId());
        model.put("paymentId", event.paymentId());
        model.put("amount", event.amount());
        model.put("currency", event.currency());
        model.put("cardBrand", event.cardBrand());
        model.put("cardLast4", event.cardLast4());
        model.put("failureMessage", event.failureMessage());

        switch (event.eventType()) {
            case PAYMENT_SUCCEEDED -> dispatcher.email(event.eventId(), event.userId(),
                    "payment-receipt", "Receipt for order #" + event.orderId(), model);
            case PAYMENT_DECLINED -> {
                dispatcher.email(event.eventId(), event.userId(),
                        "payment-declined", "Payment for order #" + event.orderId() + " didn't go through", model);
                dispatcher.sms(event.eventId(), event.userId(), "payment-declined",
                        "Mobi: your payment for order #" + event.orderId() + " was declined. Your items are held for a few minutes; try another card.");
            }
            case PAYMENT_REFUNDED -> dispatcher.email(event.eventId(), event.userId(),
                    "payment-refunded", "Refund for order #" + event.orderId(), model);
        }
    }
}
