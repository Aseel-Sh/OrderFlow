package com.orderflow.notificationservice.service;

import com.orderflow.notificationservice.entity.ProcessedEventEntity;
import com.orderflow.notificationservice.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    public IdempotencyService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    public boolean isProcessed(UUID eventId) {
        return processedEventRepository.existsById(eventId);
    }

    public void markProcessed(UUID eventId, String consumerGroup) {
        ProcessedEventEntity entity = new ProcessedEventEntity();
        entity.setEventId(eventId);
        entity.setConsumerGroup(consumerGroup);
        entity.setProcessedAt(Instant.now());
        processedEventRepository.save(entity);
    }
}

