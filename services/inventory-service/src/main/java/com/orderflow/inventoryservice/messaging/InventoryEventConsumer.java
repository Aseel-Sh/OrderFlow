package com.orderflow.inventoryservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.contracts.event.OrderCreatedEvent;
import com.orderflow.inventoryservice.config.TopicNames;
import com.orderflow.inventoryservice.service.IdempotencyService;
import com.orderflow.inventoryservice.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventConsumer {

    private static final String GROUP_NAME = "inventory-service";

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;
    private final IdempotencyService idempotencyService;

    public InventoryEventConsumer(ObjectMapper objectMapper,
                                  InventoryService inventoryService,
                                  IdempotencyService idempotencyService) {
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
        this.idempotencyService = idempotencyService;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2.0), dltTopicSuffix = ".dlq")
    @KafkaListener(topics = TopicNames.ORDER_CREATED, groupId = GROUP_NAME)
    public void onOrderCreated(String message) {
        OrderCreatedEvent event = read(message, OrderCreatedEvent.class);
        if (idempotencyService.isProcessed(event.eventId())) {
            return;
        }
        inventoryService.reserveStock(event);
        idempotencyService.markProcessed(event.eventId(), GROUP_NAME);
    }

    private <T> T read(String message, Class<T> type) {
        try {
            return objectMapper.readValue(message, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read inventory event", exception);
        }
    }
}
