package com.mobi.procurement.controller;

import com.mobi.procurement.dto.PurchaseOrderResponse;
import com.mobi.procurement.dto.ReceiptRequest;
import com.mobi.procurement.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderControllerTest {

    @Test
    void submitThatLosesTheIdempotencyKeyRaceGetsTheWinnersResult() {
        PurchaseOrderService service = mock(PurchaseOrderService.class);
        PurchaseOrderResponse winner = mock(PurchaseOrderResponse.class);
        when(service.receive(any(), eq(7L), eq("key"), any()))
                .thenThrow(new DataIntegrityViolationException("uk_receipts_receipt_key"))
                .thenReturn(winner);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("warehouse").claim("role", "WAREHOUSE")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        ReceiptRequest request = new ReceiptRequest(null, List.of(new ReceiptRequest.Line(1L, 1, 0)));

        assertSame(winner, new PurchaseOrderController(service).receive(jwt, 7L, "key", request));
        verify(service, times(2)).receive(any(), eq(7L), eq("key"), any());
    }
}
