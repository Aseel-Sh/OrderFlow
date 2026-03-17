package com.orderflow.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.contracts.event.PaymentRequestedEvent;
import com.orderflow.contracts.event.PaymentResultEvent;
import com.orderflow.paymentservice.config.TopicNames;
import com.orderflow.paymentservice.entity.PaymentEntity;
import com.orderflow.paymentservice.entity.PaymentStatus;
import com.orderflow.paymentservice.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          OutboxService outboxService,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResultEvent process(PaymentRequestedEvent event) {
        PaymentEntity payment = new PaymentEntity();
        payment.setOrderId(event.orderId());
        payment.setAmount(event.amount());
        payment.setCurrency(event.currency());
        payment.setPaymentMethod(event.paymentMethod());
        payment.setCreatedAt(Instant.now());

        boolean declined = "DECLINED".equalsIgnoreCase(event.paymentMethod());
        if (declined) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment provider rejected payment method");
        } else {
            payment.setStatus(PaymentStatus.SUCCEEDED);
        }

        paymentRepository.save(payment);

        PaymentResultEvent resultEvent = new PaymentResultEvent(
                UUID.randomUUID(),
                "v1",
                event.orderId(),
                payment.getStatus().name(),
                payment.getFailureReason(),
                Instant.now(),
                event.correlationId()
        );
        outboxService.queue(TopicNames.PAYMENT_EVENTS, event.orderId().toString(), write(resultEvent));
        return resultEvent;
    }

    public List<PaymentEntity> getPaymentsForOrder(UUID orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write payment event", exception);
        }
    }
}
