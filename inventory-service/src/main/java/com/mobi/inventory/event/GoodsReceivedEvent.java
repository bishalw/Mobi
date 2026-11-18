package com.mobi.inventory.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The part of a procurement-events record this service needs. Other event types share
 * the topic and are ignored; unknown fields are ignored so procurement can add more.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoodsReceivedEvent(String eventId, String type, Long receiptId, String poNumber, String actor,
                                 List<Line> lines) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(Long productId, Integer quantityGood, Integer quantityDamaged) {
    }
}
