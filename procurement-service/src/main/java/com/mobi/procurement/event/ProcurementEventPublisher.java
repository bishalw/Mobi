package com.mobi.procurement.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobi.procurement.entity.PurchaseOrder;
import com.mobi.procurement.entity.Supplier;
import com.mobi.procurement.outbox.OutboxEvent;
import com.mobi.procurement.outbox.OutboxEventRepository;
import com.mobi.procurement.outbox.OutboxTracing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes procurement events to the outbox in the caller's transaction, so a PO's new
 * status and the event announcing it commit together or not at all.
 */
@Component
public class ProcurementEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final String topic;
    private final OutboxTracing outboxTracing;

    public ProcurementEventPublisher(OutboxEventRepository outboxEventRepository,
                                     @Value("${procurement.kafka.topic}") String topic,
                                     OutboxTracing outboxTracing) {
        this.outboxEventRepository = outboxEventRepository;
        this.topic = topic;
        this.outboxTracing = outboxTracing;
    }

    /** Status change events: the PO and its ordered lines. */
    public void publish(ProcurementEventType type, PurchaseOrder po, Supplier supplier, String actor, String note) {
        List<ProcurementEvent.Line> lines = po.getLines().stream()
                .map(line -> new ProcurementEvent.Line(line.getProductId(), line.getQuantityOrdered(), null, null, null))
                .toList();
        publish(type, po, supplier, actor, note, null, null, lines);
    }

    /**
     * The supplier's name and contact email ride along so consumers (notification emails)
     * need no call back into this service.
     */
    public void publish(ProcurementEventType type, PurchaseOrder po, Supplier supplier, String actor, String note,
                        Long shipmentNoticeId, Long receiptId, List<ProcurementEvent.Line> lines) {
        ProcurementEvent event = new ProcurementEvent(UUID.randomUUID().toString(), type, Instant.now(),
                po.getId(), po.getPoNumber(), po.getSupplierId(),
                supplier == null ? null : supplier.getName(), supplier == null ? null : supplier.getContactEmail(),
                po.getStatus().name(), actor, note,
                shipmentNoticeId, receiptId, lines);
        try {
            OutboxEvent row = new OutboxEvent(topic, String.valueOf(po.getId()), po.getPoNumber(), type.name(),
                    objectMapper.writeValueAsString(event));
            row.setTraceparent(outboxTracing.currentTraceparent());
            outboxEventRepository.save(row);
        } catch (JsonProcessingException exp) {
            throw new IllegalStateException("Unable to serialize procurement event", exp);
        }
    }
}
