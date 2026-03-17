package com.orderflow.contracts.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        String eventVersion,
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        String paymentMethod,
        List<OrderItemPayload> items,
        Instant createdAt,
        String correlationId
) {
}

