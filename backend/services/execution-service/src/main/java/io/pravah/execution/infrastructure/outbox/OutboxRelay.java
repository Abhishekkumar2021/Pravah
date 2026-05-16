package io.pravah.execution.infrastructure.outbox;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.execution.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.execution.infrastructure.persistence.repository.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to Kafka (transactional outbox pattern, ADR-004).
 *
 * <p>Uses the {@code topic} and {@code partition_key} columns stored in the outbox row. Uses
 * pessimistic locking ({@code SELECT ... FOR UPDATE SKIP LOCKED}) for safe concurrent relays.
 *
 * <p>Dead letter: rows exceeding {@code maxPublishRetries} are marked dead-lettered (published_at
 * set to epoch) and logged at ERROR. Monitor "dead-lettered outbox" logs for alerting.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
@ConditionalOnProperty(name = "pravah.outbox.relay.enabled", havingValue = "true")
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final Instant DEAD_LETTER_MARKER = Instant.EPOCH;

  private final OutboxRepository outboxRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final int batchSize;

  public OutboxRelay(
      OutboxRepository outboxRepository,
      KafkaTemplate<String, Object> kafkaTemplate,
      @Value("${pravah.outbox.relay.batch-size:50}") int batchSize) {
    this.outboxRepository = outboxRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${pravah.outbox.relay.fixed-delay-ms:2000}")
  @Transactional
  public void publishPending() {
    List<OutboxEntity> batch =
        outboxRepository.findUnpublishedForUpdate(PageRequest.of(0, batchSize));
    for (OutboxEntity row : batch) {
      String topic = row.getTopic();
      String partitionKey = row.getPartitionKey();
      try {
        kafkaTemplate.send(topic, partitionKey, row.getPayload()).get(15, TimeUnit.SECONDS);
        row.setPublishedAt(Instant.now());
        log.info(
            "Published outbox event to Kafka",
            kv("outbox_id", row.getId()),
            kv("event_type", row.getEventType()),
            kv("topic", topic));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Outbox relay interrupted", kv("outbox_id", row.getId()));
        break;
      } catch (ExecutionException | TimeoutException e) {
        row.incrementRetryCount();
        if (row.getRetryCount() >= OutboxPublishPolicy.MAX_PUBLISH_RETRIES) {
          row.setPublishedAt(DEAD_LETTER_MARKER);
          log.error(
              "Dead-lettered outbox event after max retries",
              kv("outbox_id", row.getId()),
              kv("event_type", row.getEventType()),
              kv("topic", topic),
              kv("retry_count", row.getRetryCount()),
              kv("error", e.getMessage()));
        } else {
          log.warn(
              "Kafka publish failed; will retry",
              kv("outbox_id", row.getId()),
              kv("topic", topic),
              kv("retry_count", row.getRetryCount()),
              kv("error", e.getMessage()));
        }
      }
    }
  }
}
