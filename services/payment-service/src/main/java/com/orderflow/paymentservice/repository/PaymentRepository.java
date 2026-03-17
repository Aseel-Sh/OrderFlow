package com.orderflow.paymentservice.repository;

import com.orderflow.paymentservice.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    List<PaymentEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}

