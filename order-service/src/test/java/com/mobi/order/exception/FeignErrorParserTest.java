package com.mobi.order.exception;

import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeignErrorParserTest {

    @Test
    void extractsMessageFromJsonBody() {
        FeignException exception = exceptionWithBody("{\"message\":\"Inventory is gone\"}");

        assertEquals("Inventory is gone", FeignErrorParser.extractMessage(exception, "fallback"));
    }

    @Test
    void fallsBackForBlankBody() {
        FeignException exception = exceptionWithBody("");

        assertEquals("fallback", FeignErrorParser.extractMessage(exception, "fallback"));
    }

    @Test
    void fallsBackForMalformedJson() {
        FeignException exception = exceptionWithBody("not-json");

        assertEquals("fallback", FeignErrorParser.extractMessage(exception, "fallback"));
    }

    @Test
    void fallsBackWhenMessageIsMissing() {
        FeignException exception = exceptionWithBody("{\"error\":\"Conflict\"}");

        assertEquals("fallback", FeignErrorParser.extractMessage(exception, "fallback"));
    }

    private FeignException exceptionWithBody(String body) {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "/inventoryapi/reservations",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        Response response = Response.builder()
                .status(409)
                .reason("Conflict")
                .request(request)
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("InventoryClient#batchReserve", response);
    }
}
