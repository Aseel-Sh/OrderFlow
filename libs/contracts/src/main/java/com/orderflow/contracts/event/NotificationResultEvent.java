package com.orderflow.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record NotificationResultEvent(
        UUID eventId,
        String eventVersion,
        UUID orderId,
        String status,
        String channel,
        Instant createdAt,
        String correlationId
) {
}

