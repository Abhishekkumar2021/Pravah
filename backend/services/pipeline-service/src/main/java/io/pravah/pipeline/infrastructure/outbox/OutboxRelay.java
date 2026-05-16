package io.pravah.pipeline.infrastructure.outbox;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.pipeline.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to Kafka (transactional outbox pattern).
 *
 * <p>Uses pessimistic locking ({@code SELECT ... FOR UPDATE SKIP LOCKED}) to allow concurrent relay
 * instances without double-publishing.
 *
 * <p>Dead letter: rows exceeding {@code MAX_PUBLISH_RETRIES} are marked dead-lettered (published_at
 * set to epoch) and logged at ERROR. Monitor "dead-lettered outbox" logs for alerting.
 */
@Component
@ConditionalOnProperty(name = "pravah.outbox.relay.enabled", havingValue = "true")
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int MAX_PUBLISH_RETRIES = 10;
  private static final Instant DEAD_LETTER_MARKER = Instant.EPOCH;

  private final OutboxRepository outboxRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final String topic;

  public OutboxRelay(
      OutboxRepository outboxRepository,
      KafkaTemplate<String, Object> kafkaTemplate,
      @Value("${pravah.outbox.topic.pipeline-events}") String topic) {
    this.outboxRepository = outboxRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @Scheduled(fixedDelayString = "${pravah.outbox.relay.fixed-delay-ms:2000}")
  @Transactional
  public void publishPending() {
    List<OutboxEntity> batch = outboxRepository.findUnpublishedForUpdate(PageRequest.of(0, 50));
    for (OutboxEntity row : batch) {
      try {
        kafkaTemplate
            .send(topic, row.getAggregateId().toString(), row.getPayload())
            .get(15, TimeUnit.SECONDS);
        row.setPublishedAt(Instant.now());
        log.info(
            "Published outbox event to Kafka",
            kv("outbox_id", row.getId()),
            kv("event_type", row.getEventType()));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Outbox relay interrupted", kv("outbox_id", row.getId()));
        break;
      } catch (ExecutionException | TimeoutException e) {
        row.incrementRetryCount();
        if (row.getRetryCount() >= MAX_PUBLISH_RETRIES) {
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
              kv("retry_count", row.getRetryCount()),
              kv("error", e.getMessage()));
        }
      }
    }
  }
}
