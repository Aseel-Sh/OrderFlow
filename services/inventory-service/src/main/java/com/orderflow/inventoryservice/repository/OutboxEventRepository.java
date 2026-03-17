package com.orderflow.inventoryservice.repository;

import com.orderflow.inventoryservice.entity.OutboxEventEntity;
import com.orderflow.inventoryservice.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findTop50ByStatusInOrderByIdAsc(Collection<OutboxStatus> statuses);

    long countByStatusIn(Collection<OutboxStatus> statuses);
}

