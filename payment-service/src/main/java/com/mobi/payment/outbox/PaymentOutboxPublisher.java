package com.mobi.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobi.payment.event.PaymentEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Duration retention;
    private final OutboxTracing outboxTracing;

    public PaymentOutboxPublisher(OutboxEventRepository outboxEventRepository,
                                KafkaTemplate<String, PaymentEvent> kafkaTemplate,
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
                PaymentEvent payload = objectMapper.readValue(event.getPayloadJson(), PaymentEvent.class);
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

    private void send(OutboxEvent event, PaymentEvent payload) {
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
