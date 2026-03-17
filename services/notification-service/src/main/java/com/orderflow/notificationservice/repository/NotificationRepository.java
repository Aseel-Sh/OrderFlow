package com.orderflow.notificationservice.repository;

import com.orderflow.notificationservice.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}

