package io.pravah.pipeline.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.pipeline.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.OutboxRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  private static final String TOPIC = "pravah.pipeline.pipeline.events";

  @Mock private OutboxRepository outboxRepository;
  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  private OutboxRelay outboxRelay;

  @BeforeEach
  void setUp() {
    outboxRelay = new OutboxRelay(outboxRepository, kafkaTemplate, TOPIC);
  }

  @Test
  void publishPending_emptyBatch_doesNothing() {
    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class)))
        .thenReturn(Collections.emptyList());

    outboxRelay.publishPending();

    verify(kafkaTemplate, never()).send(any(), any(), any());
  }

  @Test
  void publishPending_singleMessage_publishesAndMarksPublished() {
    UUID aggregateId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("key", "value");
    OutboxEntity outbox = createOutboxEntity(aggregateId, payload);

    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class)))
        .thenReturn(List.of(outbox));

    CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
    future.complete(createSendResult());
    when(kafkaTemplate.send(eq(TOPIC), eq(aggregateId.toString()), eq(payload))).thenReturn(future);

    outboxRelay.publishPending();

    verify(kafkaTemplate).send(TOPIC, aggregateId.toString(), payload);
    assertThat(outbox.getPublishedAt()).isNotNull();
  }

  @Test
  void publishPending_multipleMessages_publishesAllInOrder() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    OutboxEntity outbox1 = createOutboxEntity(id1, Map.of("seq", 1));
    OutboxEntity outbox2 = createOutboxEntity(id2, Map.of("seq", 2));
    OutboxEntity outbox3 = createOutboxEntity(id3, Map.of("seq", 3));

    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class)))
        .thenReturn(List.of(outbox1, outbox2, outbox3));

    CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
    future.complete(createSendResult());
    when(kafkaTemplate.send(eq(TOPIC), any(), any())).thenReturn(future);

    outboxRelay.publishPending();

    verify(kafkaTemplate).send(TOPIC, id1.toString(), Map.of("seq", 1));
    verify(kafkaTemplate).send(TOPIC, id2.toString(), Map.of("seq", 2));
    verify(kafkaTemplate).send(TOPIC, id3.toString(), Map.of("seq", 3));

    assertThat(outbox1.getPublishedAt()).isNotNull();
    assertThat(outbox2.getPublishedAt()).isNotNull();
    assertThat(outbox3.getPublishedAt()).isNotNull();
  }

  @Test
  void publishPending_kafkaFailure_doesNotMarkPublished() {
    UUID aggregateId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("key", "value");
    OutboxEntity outbox = createOutboxEntity(aggregateId, payload);

    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class)))
        .thenReturn(List.of(outbox));

    CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException("Kafka unavailable"));
    when(kafkaTemplate.send(eq(TOPIC), eq(aggregateId.toString()), eq(payload))).thenReturn(future);

    outboxRelay.publishPending();

    assertThat(outbox.getPublishedAt()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishPending_kafkaTimeout_doesNotMarkPublished() throws Exception {
    UUID aggregateId = UUID.randomUUID();
    Map<String, Object> payload = Map.of("key", "value");
    OutboxEntity outbox = createOutboxEntity(aggregateId, payload);

    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class)))
        .thenReturn(List.of(outbox));

    CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
    when(future.get(15, java.util.concurrent.TimeUnit.SECONDS))
        .thenThrow(new TimeoutException("Timeout"));
    when(kafkaTemplate.send(eq(TOPIC), eq(aggregateId.toString()), eq(payload))).thenReturn(future);

    outboxRelay.publishPending();

    assertThat(outbox.getPublishedAt()).isNull();
  }

  @Test
  void publishPending_partialFailure_marksOnlySuccessful() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    OutboxEntity outbox1 = createOutboxEntity(id1, Map.of("seq", 1));
    OutboxEntity outbox2 = createOutboxEntity(id2, Map.of("seq", 2));

    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class)))
        .thenReturn(List.of(outbox1, outbox2));

    CompletableFuture<SendResult<String, Object>> successFuture = new CompletableFuture<>();
    successFuture.complete(createSendResult());

    CompletableFuture<SendResult<String, Object>> failFuture = new CompletableFuture<>();
    failFuture.completeExceptionally(new RuntimeException("Failed"));

    when(kafkaTemplate.send(eq(TOPIC), eq(id1.toString()), any())).thenReturn(successFuture);
    when(kafkaTemplate.send(eq(TOPIC), eq(id2.toString()), any())).thenReturn(failFuture);

    outboxRelay.publishPending();

    assertThat(outbox1.getPublishedAt()).isNotNull();
    assertThat(outbox2.getPublishedAt()).isNull();
  }

  @Test
  void publishPending_usesCorrectBatchSize() {
    when(outboxRepository.findUnpublishedForUpdate(any(Pageable.class)))
        .thenReturn(Collections.emptyList());

    outboxRelay.publishPending();

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(outboxRepository).findUnpublishedForUpdate(pageableCaptor.capture());

    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageSize()).isEqualTo(50);
    assertThat(pageable.getPageNumber()).isEqualTo(0);
  }

  private OutboxEntity createOutboxEntity(UUID aggregateId, Map<String, Object> payload) {
    return new OutboxEntity("Pipeline", aggregateId, "pipeline.created", payload, Instant.now());
  }

  private SendResult<String, Object> createSendResult() {
    RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 0), 0, 0, 0, 0, 0);
    return new SendResult<>(new ProducerRecord<>(TOPIC, "key", "value"), metadata);
  }
}
