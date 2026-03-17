package com.orderflow.paymentservice.config;

public final class TopicNames {

    private TopicNames() {
    }

    public static final String PAYMENT_REQUESTED = "payment.requested.v1";
    public static final String PAYMENT_REQUESTED_DLQ = PAYMENT_REQUESTED + ".dlq";
    public static final String PAYMENT_EVENTS = "payment.events.v1";
}

