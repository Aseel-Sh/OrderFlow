package com.orderflow.orderservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.contracts.event.InventoryResultEvent;
import com.orderflow.contracts.event.PaymentResultEvent;
import com.orderflow.orderservice.service.IdempotencyService;
import com.orderflow.orderservice.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

	private static final String GROUP_NAME = "order-service";

	private final ObjectMapper objectMapper;
	private final OrderService orderService;
	private final IdempotencyService idempotencyService;

	public OrderEventConsumer(ObjectMapper objectMapper,
							  OrderService orderService,
							  IdempotencyService idempotencyService) {
		this.objectMapper = objectMapper;
		this.orderService = orderService;
		this.idempotencyService = idempotencyService;
	}

	@KafkaListener(topics = "inventory.events.v1", groupId = GROUP_NAME)
	public void onInventoryResult(String message) {
		InventoryResultEvent event = read(message, InventoryResultEvent.class);
		if (idempotencyService.isProcessed(event.eventId())) {
			return;
		}
		orderService.onInventoryResult(event);
		idempotencyService.markProcessed(event.eventId(), GROUP_NAME);
	}

	@KafkaListener(topics = "payment.events.v1", groupId = GROUP_NAME)
	public void onPaymentResult(String message) {
		PaymentResultEvent event = read(message, PaymentResultEvent.class);
		if (idempotencyService.isProcessed(event.eventId())) {
			return;
		}
		orderService.onPaymentResult(event);
		idempotencyService.markProcessed(event.eventId(), GROUP_NAME);
	}

	private <T> T read(String message, Class<T> targetType) {
		try {
			return objectMapper.readValue(message, targetType);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to parse Kafka message", exception);
		}
	}
}

