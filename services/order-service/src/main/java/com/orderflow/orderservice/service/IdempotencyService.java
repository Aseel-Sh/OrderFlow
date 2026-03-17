package com.orderflow.orderservice.service;

import com.orderflow.orderservice.entity.ProcessedEventEntity;
import com.orderflow.orderservice.repository.ProcessedEventRepository;
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
		ProcessedEventEntity processedEventEntity = new ProcessedEventEntity();
		processedEventEntity.setEventId(eventId);
		processedEventEntity.setConsumerGroup(consumerGroup);
		processedEventEntity.setProcessedAt(Instant.now());
		processedEventRepository.save(processedEventEntity);
	}
}

