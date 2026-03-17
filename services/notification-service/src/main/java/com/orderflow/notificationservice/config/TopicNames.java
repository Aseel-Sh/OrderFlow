package com.orderflow.notificationservice.config;

public final class TopicNames {

    private TopicNames() {
    }

    public static final String ORDER_EVENTS = "order.events.v1";
    public static final String ORDER_EVENTS_DLQ = ORDER_EVENTS + ".dlq";
    public static final String NOTIFICATION_EVENTS = "notification.events.v1";
}

