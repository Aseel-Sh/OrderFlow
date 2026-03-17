package com.orderflow.inventoryservice.controller;

import com.orderflow.inventoryservice.entity.InventoryStockEntity;
import com.orderflow.inventoryservice.service.DlqAdminService;
import com.orderflow.inventoryservice.service.InventoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
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

@Validated
@RestController
@RequestMapping("/internal/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final DlqAdminService dlqAdminService;

    public InventoryController(InventoryService inventoryService,
                               DlqAdminService dlqAdminService) {
        this.inventoryService = inventoryService;
        this.dlqAdminService = dlqAdminService;
    }

    @GetMapping("/{skuId}")
    public ResponseEntity<InventoryStockEntity> getStock(@PathVariable String skuId) {
        InventoryStockEntity stock = inventoryService.getStock(skuId);
        return stock == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(stock);
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
