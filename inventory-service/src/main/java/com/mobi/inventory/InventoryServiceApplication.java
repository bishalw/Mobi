package com.mobi.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableKafka is required: Kafka clients are built by hand (no Boot autoconfig), and
// without it @KafkaListener methods such as GoodsReceivedListener are silently ignored.
@EnableKafka
@EnableScheduling
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
