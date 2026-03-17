package com.orderflow.orderservice.controller;

import com.orderflow.orderservice.dto.CreateOrderRequest;
import com.orderflow.orderservice.dto.CreateOrderResponse;
import com.orderflow.orderservice.dto.OrderResponse;
import com.orderflow.orderservice.dto.OrderStatusResponse;
import com.orderflow.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request,
                                           @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String resolvedCorrelationId = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
        return orderService.createOrder(request, resolvedCorrelationId);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return orderService.getOrder(orderId);
    }

    @GetMapping("/{orderId}/status")
    public OrderStatusResponse getStatus(@PathVariable UUID orderId) {
        return orderService.getOrderStatus(orderId);
    }
}

