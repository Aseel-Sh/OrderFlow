package com.orderflow.inventoryservice.repository;

import com.orderflow.inventoryservice.entity.InventoryStockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryStockRepository extends JpaRepository<InventoryStockEntity, String> {
}

