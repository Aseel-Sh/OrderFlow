package com.orderflow.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        String skuId,
        int quantity,
        BigDecimal unitPrice
) {
}

