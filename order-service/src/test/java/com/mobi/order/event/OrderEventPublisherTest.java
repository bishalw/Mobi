package com.mobi.order.event;

import com.mobi.order.entity.CustomerOrder;
import com.mobi.order.enums.OrderStatus;
import com.mobi.order.outbox.OutboxEvent;
import com.mobi.order.outbox.OutboxEventRepository;
import com.mobi.order.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventPublisherTest {

    @Test
    void publishEnqueuesOrderEventInOutbox() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OrderEventPublisher publisher = new OrderEventPublisher(repository, "order-events", com.mobi.order.outbox.OutboxTracing.noop());
        CustomerOrder order = new CustomerOrder();
        order.setId(42L);
        order.setUserId(7L);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(BigDecimal.TEN);

        publisher.publish(OrderEventType.ORDER_CONFIRMED, order);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals("order-events", event.getTopic());
        assertEquals("42", event.getEventKey());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertTrue(event.getPayloadJson().contains("\"eventType\":\"ORDER_CONFIRMED\""));
        assertTrue(event.getPayloadJson().contains("\"orderId\":42"));
    }
}
