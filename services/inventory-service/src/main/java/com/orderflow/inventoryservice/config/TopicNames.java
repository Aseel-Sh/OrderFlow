package com.orderflow.inventoryservice.config;

public final class TopicNames {

    private TopicNames() {
    }

    public static final String ORDER_CREATED = "order.created.v1";
    public static final String ORDER_CREATED_DLQ = ORDER_CREATED + ".dlq";
    public static final String INVENTORY_EVENTS = "inventory.events.v1";
}
