package com.orderflow.orderservice.repository;

import com.orderflow.orderservice.entity.OutboxEventEntity;
import com.orderflow.orderservice.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findTop50ByStatusInOrderByIdAsc(Collection<OutboxStatus> statuses);

    long countByStatusIn(Collection<OutboxStatus> statuses);
}

