package com.orderflow.notificationservice.service;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class DlqAdminService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String bootstrapServers;
    private final String sourceTopic;
    private final String deadLetterTopic;

    public DlqAdminService(KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                           @Value("${notification.dlq.source-topic}") String sourceTopic,
                           @Value("${notification.dlq.topic}") String deadLetterTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.bootstrapServers = bootstrapServers;
        this.sourceTopic = sourceTopic;
        this.deadLetterTopic = deadLetterTopic;
    }

    public List<DlqMessageView> inspect(int limit) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-dlq-inspector-" + System.currentTimeMillis());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            List<PartitionSnapshot> partitions = new ArrayList<>();
            for (var partitionInfo : consumer.partitionsFor(deadLetterTopic)) {
                TopicPartition topicPartition = new TopicPartition(deadLetterTopic, partitionInfo.partition());
                consumer.assign(Collections.singletonList(topicPartition));
                consumer.seekToEnd(Collections.singletonList(topicPartition));
                long endOffset = consumer.position(topicPartition);
                long startOffset = Math.max(0L, endOffset - limit);
                partitions.add(new PartitionSnapshot(topicPartition, startOffset));
            }

            for (PartitionSnapshot partition : partitions) {
                consumer.assign(Collections.singletonList(partition.topicPartition()));
                consumer.seek(partition.topicPartition(), partition.startOffset());
            }

            List<DlqMessageView> messages = new ArrayList<>();
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                messages.add(new DlqMessageView(
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        record.value(),
                        Instant.ofEpochMilli(record.timestamp())
                ));
            }
            messages.sort((left, right) -> Long.compare(right.offset(), left.offset()));
            return messages.size() > limit ? messages.subList(0, limit) : messages;
        }
    }

    public DlqReplayResult replay(int partition, long offset) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-dlq-replay-" + System.currentTimeMillis());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        TopicPartition topicPartition = new TopicPartition(deadLetterTopic, partition);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.assign(Collections.singletonList(topicPartition));
            consumer.seek(topicPartition, offset);
            ConsumerRecord<String, String> targetRecord = null;
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                if (record.offset() == offset) {
                    targetRecord = record;
                    break;
                }
            }
            if (targetRecord == null) {
                throw new IllegalArgumentException("DLQ message not found for partition=" + partition + ", offset=" + offset);
            }
            kafkaTemplate.send(sourceTopic, targetRecord.key(), targetRecord.value());
            return new DlqReplayResult(sourceTopic, deadLetterTopic, partition, offset, "REPLAYED");
        }
    }

    public Map<String, String> describe() {
        return Map.of("sourceTopic", sourceTopic, "deadLetterTopic", deadLetterTopic);
    }

    private record PartitionSnapshot(TopicPartition topicPartition, long startOffset) {
    }

    public record DlqMessageView(String topic, int partition, long offset, String key, String payload, Instant timestamp) {
    }

    public record DlqReplayResult(String sourceTopic, String deadLetterTopic, int partition, long offset, String status) {
    }
}

