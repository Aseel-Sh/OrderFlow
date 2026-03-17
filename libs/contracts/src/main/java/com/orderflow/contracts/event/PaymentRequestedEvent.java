package com.orderflow.contracts.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRequestedEvent(
        UUID eventId,
        String eventVersion,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        Instant createdAt,
        String correlationId
) {
}

