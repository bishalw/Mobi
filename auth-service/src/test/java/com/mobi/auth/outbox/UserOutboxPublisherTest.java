package com.mobi.auth.outbox;

import com.mobi.auth.event.UserEvent;
import com.mobi.auth.event.UserEventType;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserOutboxPublisherTest {

    @Test
    void publishPendingEventsMarksSuccessfulEventPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, UserEvent> kafkaTemplate = mockKafkaTemplate();
        OutboxEvent outboxEvent = new OutboxEvent(
                "user-events",
                "99",
                "99",
                "USER_REGISTERED",
                "{\"eventId\":\"evt-1\",\"eventType\":\"USER_REGISTERED\",\"userId\":99,\"username\":\"alice\",\"occurredAt\":\"2026-05-16T12:00:00\"}"
        );

        when(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.send(eq("user-events"), eq("99"), any(UserEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new UserOutboxPublisher(repository, kafkaTemplate, 7).publishPendingEvents();

        assertEquals(OutboxStatus.PUBLISHED, outboxEvent.getStatus());
    }

    @Test
    void publishPendingEventsLeavesFailedEventRetryable() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, UserEvent> kafkaTemplate = mockKafkaTemplate();
        OutboxEvent outboxEvent = new OutboxEvent(
                "user-events",
                "99",
                "99",
                "USER_REGISTERED",
                "{\"eventId\":\"evt-1\",\"eventType\":\"USER_REGISTERED\",\"userId\":99,\"username\":\"alice\",\"occurredAt\":\"2026-05-16T12:00:00\"}"
        );

        when(repository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.send(eq("user-events"), eq("99"), any(UserEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        new UserOutboxPublisher(repository, kafkaTemplate, 7).publishPendingEvents();

        assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
        assertEquals(1, outboxEvent.getAttempts());
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, UserEvent> mockKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
