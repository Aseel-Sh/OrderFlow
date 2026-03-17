package com.orderflow.paymentservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.contracts.event.PaymentRequestedEvent;
import com.orderflow.paymentservice.config.TopicNames;
import com.orderflow.paymentservice.service.IdempotencyService;
import com.orderflow.paymentservice.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final String GROUP_NAME = "payment-service";

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    public PaymentEventConsumer(ObjectMapper objectMapper,
                                PaymentService paymentService,
                                IdempotencyService idempotencyService) {
        this.objectMapper = objectMapper;
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2.0), dltTopicSuffix = ".dlq")
    @KafkaListener(topics = TopicNames.PAYMENT_REQUESTED, groupId = GROUP_NAME)
    public void onPaymentRequested(String message) {
        PaymentRequestedEvent event = read(message, PaymentRequestedEvent.class);
        if (idempotencyService.isProcessed(event.eventId())) {
            return;
        }
        paymentService.process(event);
        idempotencyService.markProcessed(event.eventId(), GROUP_NAME);
    }

    private <T> T read(String message, Class<T> type) {
        try {
            return objectMapper.readValue(message, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read payment event", exception);
        }
    }
}
