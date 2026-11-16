package com.mobi.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobi.payment.entity.Payment;
import com.mobi.payment.outbox.OutboxEvent;
import com.mobi.payment.outbox.OutboxEventRepository;
import com.mobi.payment.outbox.OutboxTracing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Writes payment events to the outbox in the caller's transaction, so a payment's new
 * status and the event announcing it commit together or not at all.
 */
@Component
public class PaymentEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final String topic;
    private final OutboxTracing outboxTracing;

    public PaymentEventPublisher(OutboxEventRepository outboxEventRepository,
                                 @Value("${payments.kafka.topic}") String topic,
                                 OutboxTracing outboxTracing) {
        this.outboxEventRepository = outboxEventRepository;
        this.topic = topic;
        this.outboxTracing = outboxTracing;
    }

    public void publish(PaymentEventType type, Payment payment) {
        try {
            // Keyed by order, so every event for one order lands on one partition in order.
            OutboxEvent event = new OutboxEvent(topic, String.valueOf(payment.getOrderId()),
                    payment.getPaymentId(), type.name(),
                    objectMapper.writeValueAsString(PaymentEvent.of(type, payment)));
            event.setTraceparent(outboxTracing.currentTraceparent());
            outboxEventRepository.save(event);
        } catch (JsonProcessingException exp) {
            throw new IllegalStateException("Unable to serialize payment event", exp);
        }
    }
}
