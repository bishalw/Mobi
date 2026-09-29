package com.mobi.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableKafka is required for @KafkaListener to be processed at all — this service only
// ever produced events until the user projection, so nothing here consumed before and its
// absence was invisible. Without it the listener bean exists and is simply never wired to
// a container, so the projection stays empty with no error anywhere in the log.
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableKafka
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
