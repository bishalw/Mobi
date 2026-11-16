package com.mobi.payment.controller;

import com.mobi.payment.dto.PageResponse;
import com.mobi.payment.dto.PaymentResponse;
import com.mobi.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/paymentapi")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentResponse get(@PathVariable String paymentId) {
        return paymentService.get(paymentId);
    }

    @GetMapping("/orders/{orderId}/payments")
    public List<PaymentResponse> forOrder(@PathVariable Long orderId) {
        return paymentService.forOrder(orderId);
    }

    @GetMapping("/admin/payments")
    public PageResponse<PaymentResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return paymentService.list(page, size);
    }

    // No admin refund here: refunds go through order-service
    // (POST /orderapi/orders/{id}/refund), which owns the order and keeps its status in
    // step with the payment. It calls the internal refund endpoint as a service.
}
