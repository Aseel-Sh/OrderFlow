package com.orderflow.orderservice.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateOrderResponse(
        UUID orderId,
        String status,
        Instant createdAt
) {
}

