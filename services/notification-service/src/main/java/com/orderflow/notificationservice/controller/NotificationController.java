package com.orderflow.notificationservice.controller;

import com.orderflow.notificationservice.entity.NotificationEntity;
import com.orderflow.notificationservice.service.DlqAdminService;
import com.orderflow.notificationservice.service.NotificationService;
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
@RequestMapping("/internal/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final DlqAdminService dlqAdminService;

    public NotificationController(NotificationService notificationService,
                                  DlqAdminService dlqAdminService) {
        this.notificationService = notificationService;
        this.dlqAdminService = dlqAdminService;
    }

    @GetMapping("/{orderId}")
    public List<NotificationEntity> list(@PathVariable UUID orderId) {
        return notificationService.getByOrderId(orderId);
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

