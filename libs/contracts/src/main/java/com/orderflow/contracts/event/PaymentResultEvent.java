package com.orderflow.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentResultEvent(
        UUID eventId,
        String eventVersion,
        UUID orderId,
        String status,
        String reason,
        Instant createdAt,
        String correlationId
) {
}

