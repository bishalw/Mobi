package com.mobi.payment.dto;

import jakarta.validation.constraints.Size;

public record RefundRequest(@Size(max = 255) String reason) {
}
