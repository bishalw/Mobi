package com.scalablerest.order.outbox;

import com.scalablerest.order.event.OrderEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderOutboxPublisherTest {

    @Test
    void publishPendingEventsMarksSuccessfulEventPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, OrderEvent> kafkaTemplate = mockKafkaTemplate();
        OutboxEvent outboxEvent = new OutboxEvent(
                "order-events",
                "42",
                "42",
                "ORDER_CONFIRMED",
                "{\"eventId\":\"evt-1\",\"eventType\":\"ORDER_CONFIRMED\",\"orderId\":42,\"userId\":7,\"orderStatus\":\"CONFIRMED\",\"totalAmount\":10,\"items\":[],\"occurredAt\":\"2026-05-16T12:00:00\"}"
        );

        when(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.send(eq("order-events"), eq("42"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new OrderOutboxPublisher(repository, kafkaTemplate, 7).publishPendingEvents();

        assertEquals(OutboxStatus.PUBLISHED, outboxEvent.getStatus());
    }

    @Test
    void publishPendingEventsLeavesFailedEventRetryable() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, OrderEvent> kafkaTemplate = mockKafkaTemplate();
        OutboxEvent outboxEvent = new OutboxEvent(
                "order-events",
                "42",
                "42",
                "ORDER_CONFIRMED",
                "{\"eventId\":\"evt-1\",\"eventType\":\"ORDER_CONFIRMED\",\"orderId\":42,\"userId\":7,\"orderStatus\":\"CONFIRMED\",\"totalAmount\":10,\"items\":[],\"occurredAt\":\"2026-05-16T12:00:00\"}"
        );

        when(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.send(eq("order-events"), eq("42"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        new OrderOutboxPublisher(repository, kafkaTemplate, 7).publishPendingEvents();

        assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
        assertEquals(1, outboxEvent.getAttempts());
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, OrderEvent> mockKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
