package com.mobi.payment.controller;

import com.mobi.payment.dto.ChargeRequest;
import com.mobi.payment.dto.PaymentResponse;
import com.mobi.payment.dto.RefundRequest;
import com.mobi.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only (SERVICE authority, from the internal shared secret). The
 * API gateway also refuses any /internal/ path from outside, so these are unreachable
 * from the internet even if the secret leaked into a browser.
 */
@RestController
@RequestMapping("/paymentapi/internal/payments")
public class InternalPaymentController {

    private final PaymentService paymentService;

    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // 200 for both outcomes: a decline is a successful call with a negative answer,
    // reported in the body's status. Non-2xx is reserved for "could not get an answer".
    @PostMapping
    public PaymentResponse charge(@Valid @RequestBody ChargeRequest request) {
        return paymentService.charge(request);
    }

    @PostMapping("/{paymentId}/refund")
    public PaymentResponse refund(@PathVariable String paymentId, @Valid @RequestBody(required = false) RefundRequest request) {
        return paymentService.refund(paymentId, request == null ? null : request.reason());
    }
}
