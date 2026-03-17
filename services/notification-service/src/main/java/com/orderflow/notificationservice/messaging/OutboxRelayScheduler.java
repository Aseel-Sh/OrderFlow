package com.orderflow.notificationservice.messaging;

import com.orderflow.notificationservice.entity.OutboxEventEntity;
import com.orderflow.notificationservice.entity.OutboxStatus;
import com.orderflow.notificationservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxRelayScheduler {

    private static final List<OutboxStatus> PENDING_STATUSES = List.of(OutboxStatus.NEW, OutboxStatus.FAILED);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelayScheduler(OutboxEventRepository outboxEventRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        Gauge.builder("orderflow.outbox.pending", outboxEventRepository,
                        repository -> repository.countByStatusIn(PENDING_STATUSES))
                .description("Pending outbox records waiting to be published")
                .tag("service", "notification-service")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${notification.outbox.poll-ms:1500}")
    public void relay() {
        for (OutboxEventEntity event : outboxEventRepository.findTop50ByStatusInOrderByIdAsc(PENDING_STATUSES)) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();
                event.setStatus(OutboxStatus.PUBLISHED);
            } catch (Exception exception) {
                event.setStatus(OutboxStatus.FAILED);
                event.setRetryCount(event.getRetryCount() + 1);
            }
            outboxEventRepository.save(event);
        }
    }
}

