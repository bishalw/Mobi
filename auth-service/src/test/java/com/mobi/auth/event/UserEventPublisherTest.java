package com.mobi.auth.event;

import com.mobi.auth.entity.User;
import com.mobi.auth.outbox.OutboxEvent;
import com.mobi.auth.outbox.OutboxEventRepository;
import com.mobi.auth.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserEventPublisherTest {

    @Test
    void publishEnqueuesUserEventInOutbox() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        UserEventPublisher publisher = new UserEventPublisher(repository, "user-events", com.mobi.auth.outbox.OutboxTracing.noop());
        User user = new User("alice", "hashed", "USER");
        user.setId(99L);

        publisher.publish(UserEventType.USER_REGISTERED, user);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals("user-events", event.getTopic());
        assertEquals("99", event.getEventKey());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertTrue(event.getPayloadJson().contains("\"eventType\":\"USER_REGISTERED\""));
        assertTrue(event.getPayloadJson().contains("\"username\":\"alice\""));
    }
}
