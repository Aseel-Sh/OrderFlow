package com.orderflow.paymentservice.controller;

import com.orderflow.paymentservice.entity.PaymentEntity;
import com.orderflow.paymentservice.service.DlqAdminService;
import com.orderflow.paymentservice.service.PaymentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final DlqAdminService dlqAdminService;

    public PaymentController(PaymentService paymentService,
                             DlqAdminService dlqAdminService) {
        this.paymentService = paymentService;
        this.dlqAdminService = dlqAdminService;
    }

    @GetMapping("/{orderId}")
    public List<PaymentEntity> list(@PathVariable UUID orderId) {
        return paymentService.getPaymentsForOrder(orderId);
    }

    @GetMapping("/admin/dlq")
    public Map<String, Object> inspectDlq(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return Map.of(
                "topics", dlqAdminService.describe(),
                "messages", dlqAdminService.inspect(limit)
        );
    }

    @PostMapping("/admin/dlq/replay")
    public DlqAdminService.DlqReplayResult replay(@RequestBody DlqReplayRequest request) {
        return dlqAdminService.replay(request.partition(), request.offset());
    }

    public record DlqReplayRequest(int partition, long offset) {
    }
}
