package com.orderflow.orderservice.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record OrderStatusResponse(
        UUID orderId,
        String status,
        String failureReason,
        Instant updatedAt
) implements Serializable {
}

