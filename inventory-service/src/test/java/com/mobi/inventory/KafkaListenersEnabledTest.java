package com.mobi.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.EnableKafka;

// Without @EnableKafka the receipt listener never starts and received goods never
// reach stock, with no error anywhere. Unit tests call the listener directly, so pin it.
class KafkaListenersEnabledTest {

    @Test
    void kafkaListenersAreEnabled() {
        assertThat(InventoryServiceApplication.class.isAnnotationPresent(EnableKafka.class)).isTrue();
    }
}
