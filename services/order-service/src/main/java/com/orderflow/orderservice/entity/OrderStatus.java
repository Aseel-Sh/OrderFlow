package com.orderflow.orderservice.entity;

public enum OrderStatus {
    PENDING_INVENTORY,
    PENDING_PAYMENT,
    COMPLETED,
    FAILED_INVENTORY,
    FAILED_PAYMENT
}

