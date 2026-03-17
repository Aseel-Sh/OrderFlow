package com.orderflow.orderservice.config;

public final class TopicNames {

    private TopicNames() {
    }

    public static final String ORDER_CREATED = "order.created.v1";
    public static final String INVENTORY_EVENTS = "inventory.events.v1";
    public static final String PAYMENT_REQUESTED = "payment.requested.v1";
    public static final String PAYMENT_EVENTS = "payment.events.v1";
    public static final String ORDER_EVENTS = "order.events.v1";
}

