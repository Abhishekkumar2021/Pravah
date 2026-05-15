package io.pravah.execution.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.execution.domain.JobEventTypes;
import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  private static final String EXECUTION_TOPIC = "pravah.execution.execution.events";
  private static final String JOB_TOPIC = "pravah.job.created";
  private static final int BATCH_SIZE = 50;

  @Mock private OutboxRepository outboxRepository;
  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  private OutboxRelay outboxRelay;

  @BeforeEach
  void setUp() {
    outboxRelay = new OutboxRelay(outboxRepository, kafkaTemplate, BATCH_SIZE);
  }

  @Test
  void publishPending_emptyBatch_doesNothing() {
    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class)))
        .thenReturn(Collections.emptyList());

    outboxRelay.publishPending();

    verify(kafkaTemplate, never()).send(any(), any(), any());
  }

  @Test
  void publishPending_executionCreated_usesTopicFromEntity() {
    UUID executionId = UUID.randomUUID();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("executionId", executionId.toString());
    payload.put("eventType", "execution.created");

    OutboxEntity row =
        new OutboxEntity(
            "execution",
            executionId,
            "execution.created",
            EXECUTION_TOPIC,
            executionId.toString(),
            payload,
            Instant.now());

    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class))).thenReturn(List.of(row));

    CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
    future.complete(sendResult(EXECUTION_TOPIC));
    when(kafkaTemplate.send(eq(EXECUTION_TOPIC), eq(executionId.toString()), eq(payload)))
        .thenReturn(future);

    outboxRelay.publishPending();

    verify(kafkaTemplate).send(EXECUTION_TOPIC, executionId.toString(), payload);
    assertThat(row.getPublishedAt()).isNotNull();
  }

  @Test
  void publishPending_jobCreated_usesTopicFromEntity() {
    UUID jobId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("executionId", executionId.toString());
    payload.put("jobId", jobId.toString());

    OutboxEntity row =
        new OutboxEntity(
            "job",
            jobId,
            JobEventTypes.JOB_CREATED,
            JOB_TOPIC,
            executionId.toString(),
            payload,
            Instant.now());

    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class))).thenReturn(List.of(row));

    CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
    future.complete(sendResult(JOB_TOPIC));
    when(kafkaTemplate.send(eq(JOB_TOPIC), eq(executionId.toString()), eq(payload)))
        .thenReturn(future);

    outboxRelay.publishPending();

    verify(kafkaTemplate).send(JOB_TOPIC, executionId.toString(), payload);
    assertThat(row.getPublishedAt()).isNotNull();
  }

  private static SendResult<String, Object> sendResult(String topic) {
    RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0, 0, 0);
    return new SendResult<>(new ProducerRecord<>(topic, "k", "v"), metadata);
  }
}
