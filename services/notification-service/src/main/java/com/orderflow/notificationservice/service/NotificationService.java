package com.orderflow.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.contracts.event.NotificationResultEvent;
import com.orderflow.contracts.event.OrderFinalizedEvent;
import com.orderflow.notificationservice.config.TopicNames;
import com.orderflow.notificationservice.entity.NotificationEntity;
import com.orderflow.notificationservice.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               OutboxService outboxService,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationResultEvent sendOrderUpdate(OrderFinalizedEvent event) {
        NotificationEntity entity = new NotificationEntity();
        entity.setOrderId(event.orderId());
        entity.setChannel("EMAIL");
        entity.setStatus("SENT");
        entity.setMessage(buildMessage(event));
        entity.setCreatedAt(Instant.now());
        notificationRepository.save(entity);

        NotificationResultEvent resultEvent = new NotificationResultEvent(
                UUID.randomUUID(),
                "v1",
                event.orderId(),
                "SENT",
                "EMAIL",
                Instant.now(),
                event.correlationId()
        );
        outboxService.queue(TopicNames.NOTIFICATION_EVENTS, event.orderId().toString(), write(resultEvent));
        return resultEvent;
    }

    public List<NotificationEntity> getByOrderId(UUID orderId) {
        return notificationRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    private String buildMessage(OrderFinalizedEvent event) {
        if ("COMPLETED".equals(event.finalStatus())) {
            return "Your order " + event.orderId() + " is completed.";
        }
        return "Your order " + event.orderId() + " failed: " + event.reason();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write notification event", exception);
        }
    }
}

