package com.mobi.order.client;

import com.mobi.order.dto.ChargeRequest;
import com.mobi.order.dto.PaymentResult;
import com.mobi.order.dto.RefundRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "paymentClient", url = "${services.payment-service.url}")
public interface PaymentClient {

    // Safe for Feign's Retryer to repeat after a read timeout: payment-service treats
    // idempotencyKey as the identity of the charge, so a retry returns the original
    // result instead of charging the card a second time.
    @PostMapping("/paymentapi/internal/payments")
    PaymentResult charge(@RequestBody ChargeRequest request);

    @PostMapping("/paymentapi/internal/payments/{paymentId}/refund")
    PaymentResult refund(@PathVariable String paymentId, @RequestBody RefundRequest request);
}
