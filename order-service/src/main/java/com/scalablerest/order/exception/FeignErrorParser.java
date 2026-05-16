package com.scalablerest.order.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;

public final class FeignErrorParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FeignErrorParser() {}

    public static String extractMessage(FeignException e, String fallback) {
        try {
            String body = e.contentUTF8();
            if (body == null || body.isBlank()) return fallback;
            return MAPPER.readTree(body).path("message").asText(fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
