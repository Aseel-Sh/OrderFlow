package com.orderflow.notificationservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.contracts.event.OrderFinalizedEvent;
import com.orderflow.notificationservice.config.TopicNames;
import com.orderflow.notificationservice.service.IdempotencyService;
import com.orderflow.notificationservice.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final String GROUP_NAME = "notification-service";

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;

    public NotificationEventConsumer(ObjectMapper objectMapper,
                                     NotificationService notificationService,
                                     IdempotencyService idempotencyService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.idempotencyService = idempotencyService;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2.0), dltTopicSuffix = ".dlq")
    @KafkaListener(topics = TopicNames.ORDER_EVENTS, groupId = GROUP_NAME)
    public void onOrderEvent(String message) {
        OrderFinalizedEvent event = read(message, OrderFinalizedEvent.class);
        if (idempotencyService.isProcessed(event.eventId())) {
            return;
        }
        notificationService.sendOrderUpdate(event);
        idempotencyService.markProcessed(event.eventId(), GROUP_NAME);
    }

    private <T> T read(String message, Class<T> type) {
        try {
            return objectMapper.readValue(message, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read notification event", exception);
        }
    }
}

