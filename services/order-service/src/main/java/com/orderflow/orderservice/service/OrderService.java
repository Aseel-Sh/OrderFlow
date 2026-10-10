package com.orderflow.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.contracts.event.InventoryResultEvent;
import com.orderflow.contracts.event.OrderCreatedEvent;
import com.orderflow.contracts.event.OrderFinalizedEvent;
import com.orderflow.contracts.event.OrderItemPayload;
import com.orderflow.contracts.event.PaymentRequestedEvent;
import com.orderflow.contracts.event.PaymentResultEvent;
import com.orderflow.orderservice.config.TopicNames;
import com.orderflow.orderservice.dto.CreateOrderRequest;
import com.orderflow.orderservice.dto.CreateOrderResponse;
import com.orderflow.orderservice.dto.OrderItemResponse;
import com.orderflow.orderservice.dto.OrderResponse;
import com.orderflow.orderservice.dto.OrderStatusResponse;
import com.orderflow.orderservice.entity.OrderEntity;
import com.orderflow.orderservice.entity.OrderItemEntity;
import com.orderflow.orderservice.entity.OrderStatus;
import com.orderflow.orderservice.exception.NotFoundException;
import com.orderflow.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    public OrderService(OrderRepository orderRepository,
                        OutboxService outboxService,
                        ObjectMapper objectMapper,
                        CacheManager cacheManager) {
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request, String correlationId) {
        OrderEntity order = new OrderEntity();
        order.setId(UUID.randomUUID());
        order.setCustomerId(request.customerId());
        order.setStatus(OrderStatus.PENDING_INVENTORY);
        order.setCurrency("USD");
        order.setPaymentMethod(request.paymentMethod());
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setTotalAmount(request.items().stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        request.items().forEach(item -> {
            OrderItemEntity orderItemEntity = new OrderItemEntity();
            orderItemEntity.setSkuId(item.skuId());
            orderItemEntity.setQuantity(item.quantity());
            orderItemEntity.setUnitPrice(item.unitPrice());
            order.addItem(orderItemEntity);
        });

        orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                "v1",
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getPaymentMethod(),
                order.getItems().stream().map(it -> new OrderItemPayload(it.getSkuId(), it.getQuantity(), it.getUnitPrice())).toList(),
                Instant.now(),
                correlationId
        );

        outboxService.queue(TopicNames.ORDER_CREATED, order.getId().toString(), writeAsJson(event));
        updateStatusCache(order);

        return new CreateOrderResponse(order.getId(), order.getStatus().name(), order.getCreatedAt());
    }

    public OrderResponse getOrder(UUID orderId) {
        OrderEntity order = load(orderId);
        return toResponse(order);
    }

    public OrderStatusResponse getOrderStatus(UUID orderId) {
        try {
            Cache cache = cacheManager.getCache("orderStatus");
            if (cache != null) {
                OrderStatusResponse cached = cache.get(orderId, OrderStatusResponse.class);
                if (cached != null) {
                    return cached;
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to read order status cache for orderId={}", orderId, exception);
        }

        OrderEntity order = load(orderId);
        OrderStatusResponse response = new OrderStatusResponse(
                order.getId(),
                order.getStatus().name(),
                order.getFailureReason(),
                order.getUpdatedAt()
        );
        updateStatusCache(order);
        return response;
    }

    @Transactional
    public void onInventoryResult(InventoryResultEvent event) {
        OrderEntity order = load(event.orderId());
        if ("RESERVED".equals(event.status()) && order.getStatus() == OrderStatus.PENDING_INVENTORY) {
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setUpdatedAt(Instant.now());

            PaymentRequestedEvent paymentRequestedEvent = new PaymentRequestedEvent(
                    UUID.randomUUID(),
                    "v1",
                    order.getId(),
                    order.getTotalAmount(),
                    order.getCurrency(),
                    order.getPaymentMethod(),
                    Instant.now(),
                    event.correlationId()
            );
            outboxService.queue(TopicNames.PAYMENT_REQUESTED, order.getId().toString(), writeAsJson(paymentRequestedEvent));
        }

        if ("REJECTED".equals(event.status()) && order.getStatus() == OrderStatus.PENDING_INVENTORY) {
            order.setStatus(OrderStatus.FAILED_INVENTORY);
            order.setFailureReason(event.reason());
            order.setUpdatedAt(Instant.now());
            publishFinalized(order, event.correlationId(), event.reason());
        }

        updateStatusCache(order);
    }

    @Transactional
    public void onPaymentResult(PaymentResultEvent event) {
        OrderEntity order = load(event.orderId());
        if ("SUCCEEDED".equals(event.status()) && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setUpdatedAt(Instant.now());
            publishFinalized(order, event.correlationId(), null);
        }

        if ("FAILED".equals(event.status()) && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStatus(OrderStatus.FAILED_PAYMENT);
            order.setFailureReason(event.reason());
            order.setUpdatedAt(Instant.now());
            publishFinalized(order, event.correlationId(), event.reason());
        }

        updateStatusCache(order);
    }

    private void publishFinalized(OrderEntity order, String correlationId, String reason) {
        OrderFinalizedEvent finalizedEvent = new OrderFinalizedEvent(
                UUID.randomUUID(),
                "v1",
                order.getId(),
                order.getStatus().name(),
                reason,
                Instant.now(),
                correlationId
        );
        outboxService.queue(TopicNames.ORDER_EVENTS, order.getId().toString(), writeAsJson(finalizedEvent));
    }

    private OrderEntity load(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
    }

    private OrderResponse toResponse(OrderEntity order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(item.getSkuId(), item.getQuantity(), item.getUnitPrice()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getPaymentMethod(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items
        );
    }

    private void updateStatusCache(OrderEntity order) {
        try {
            Cache cache = cacheManager.getCache("orderStatus");
            if (cache != null) {
                cache.put(order.getId(), new OrderStatusResponse(order.getId(), order.getStatus().name(), order.getFailureReason(), order.getUpdatedAt()));
            }
        } catch (RuntimeException exception) {
            // Cache availability should not break the core order processing transaction.
            log.warn("Failed to update order status cache for orderId={}", order.getId(), exception);
        }
    }

    private String writeAsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize event", exception);
        }
    }
}

