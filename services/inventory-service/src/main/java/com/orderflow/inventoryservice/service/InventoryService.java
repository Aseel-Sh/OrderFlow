package com.orderflow.inventoryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.contracts.event.InventoryResultEvent;
import com.orderflow.contracts.event.OrderCreatedEvent;
import com.orderflow.contracts.event.OrderItemPayload;
import com.orderflow.inventoryservice.config.TopicNames;
import com.orderflow.inventoryservice.entity.InventoryStockEntity;
import com.orderflow.inventoryservice.repository.InventoryStockRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryStockRepository inventoryStockRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public InventoryService(InventoryStockRepository inventoryStockRepository,
                            OutboxService outboxService,
                            ObjectMapper objectMapper) {
        this.inventoryStockRepository = inventoryStockRepository;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventoryResultEvent reserveStock(OrderCreatedEvent event) {
        InventoryResultEvent resultEvent;
        for (OrderItemPayload item : event.items()) {
            Optional<InventoryStockEntity> stockOpt = inventoryStockRepository.findById(item.skuId());
            if (stockOpt.isEmpty() || stockOpt.get().getAvailableQty() < item.quantity()) {
                resultEvent = new InventoryResultEvent(
                        UUID.randomUUID(),
                        "v1",
                        event.orderId(),
                        "REJECTED",
                        "Insufficient inventory for sku " + item.skuId(),
                        Instant.now(),
                        event.correlationId()
                );
                queueResult(resultEvent);
                return resultEvent;
            }
        }

        for (OrderItemPayload item : event.items()) {
            InventoryStockEntity stock = inventoryStockRepository.findById(item.skuId()).orElseThrow();
            stock.setAvailableQty(stock.getAvailableQty() - item.quantity());
            stock.setReservedQty(stock.getReservedQty() + item.quantity());
            stock.setUpdatedAt(Instant.now());
            inventoryStockRepository.save(stock);
        }

        resultEvent = new InventoryResultEvent(
                UUID.randomUUID(),
                "v1",
                event.orderId(),
                "RESERVED",
                null,
                Instant.now(),
                event.correlationId()
        );
        queueResult(resultEvent);
        return resultEvent;
    }

    public InventoryStockEntity getStock(String skuId) {
        return inventoryStockRepository.findById(skuId).orElse(null);
    }

    private void queueResult(InventoryResultEvent resultEvent) {
        outboxService.queue(TopicNames.INVENTORY_EVENTS, resultEvent.orderId().toString(), write(resultEvent));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write inventory event", exception);
        }
    }
}
