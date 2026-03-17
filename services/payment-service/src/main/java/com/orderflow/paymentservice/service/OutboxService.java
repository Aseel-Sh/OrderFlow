package com.orderflow.paymentservice.service;

import com.orderflow.paymentservice.entity.OutboxEventEntity;
import com.orderflow.paymentservice.entity.OutboxStatus;
import com.orderflow.paymentservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    public void queue(String topic, String key, String payload) {
        OutboxEventEntity outboxEventEntity = new OutboxEventEntity();
        outboxEventEntity.setTopic(topic);
        outboxEventEntity.setEventKey(key);
        outboxEventEntity.setPayload(payload);
        outboxEventEntity.setStatus(OutboxStatus.NEW);
        outboxEventEntity.setCreatedAt(Instant.now());
        outboxEventEntity.setRetryCount(0);
        outboxEventRepository.save(outboxEventEntity);
    }
}

