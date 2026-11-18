package com.mobi.order.dto;

import jakarta.validation.constraints.Size;

// restock: also put the items back into available stock (the goods came back).
// Boxed so a body without "restock" (e.g. "{}") means false instead of a 400.
public record RefundOrderRequest(@Size(max = 255) String reason, Boolean restock) {

    public boolean restockRequested() {
        return Boolean.TRUE.equals(restock);
    }
}
