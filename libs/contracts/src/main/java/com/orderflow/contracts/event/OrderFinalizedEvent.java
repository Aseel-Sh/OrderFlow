package com.orderflow.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record OrderFinalizedEvent(
        UUID eventId,
        String eventVersion,
        UUID orderId,
        String finalStatus,
        String reason,
        Instant createdAt,
        String correlationId
) {
}

