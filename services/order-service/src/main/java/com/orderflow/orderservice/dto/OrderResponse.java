package com.orderflow.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        String paymentMethod,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items
) {
}

