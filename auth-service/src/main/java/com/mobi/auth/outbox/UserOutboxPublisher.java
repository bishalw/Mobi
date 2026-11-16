package com.mobi.auth.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobi.auth.event.UserEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
public class UserOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Duration retention;
    private final OutboxTracing outboxTracing;

    public UserOutboxPublisher(OutboxEventRepository outboxEventRepository,
                               KafkaTemplate<String, UserEvent> kafkaTemplate,
                               @Value("${outbox.retention-days:7}") long retentionDays,
                               OutboxTracing outboxTracing) {
        this.outboxTracing = outboxTracing;
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)) {
            try {
                UserEvent payload = objectMapper.readValue(event.getPayloadJson(), UserEvent.class);
                outboxTracing.runInTrace(event.getTraceparent(), "outbox publish " + event.getTopic(), () -> send(event, payload));
                event.markPublished();
            } catch (Exception exp) {
                event.markFailed(exp.getMessage());
            }
        }
    }

    @Scheduled(cron = "${outbox.cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void cleanupPublishedEvents() {
        outboxEventRepository.deleteByStatusAndPublishedAtBefore(
                OutboxStatus.PUBLISHED,
                LocalDateTime.now().minus(retention)
        );
    }

    private void send(OutboxEvent event, UserEvent payload) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), payload).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exp) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event " + event.getId(), exp);
        } catch (Exception exp) {
            throw new IllegalStateException(exp.getMessage(), exp);
        }
    }
}
