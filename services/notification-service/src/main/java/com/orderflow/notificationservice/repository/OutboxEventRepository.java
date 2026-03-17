package com.orderflow.notificationservice.repository;

import com.orderflow.notificationservice.entity.OutboxEventEntity;
import com.orderflow.notificationservice.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findTop50ByStatusInOrderByIdAsc(Collection<OutboxStatus> statuses);

    long countByStatusIn(Collection<OutboxStatus> statuses);
}

