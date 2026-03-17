package com.orderflow.contracts.event;

import java.math.BigDecimal;

public record OrderItemPayload(
        String skuId,
        int quantity,
        BigDecimal unitPrice
) {
}

