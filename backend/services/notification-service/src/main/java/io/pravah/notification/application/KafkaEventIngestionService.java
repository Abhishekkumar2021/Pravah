package io.pravah.notification.application;

import io.pravah.notification.infrastructure.persistence.entity.ProcessedEventEntity;
import io.pravah.notification.infrastructure.persistence.repository.ProcessedEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent Kafka event ingestion (dedupe on eventId per LLD §16). */
@Service
public class KafkaEventIngestionService {

  private final ProcessedEventRepository processedEventRepository;

  public KafkaEventIngestionService(ProcessedEventRepository processedEventRepository) {
    this.processedEventRepository = processedEventRepository;
  }

  /**
   * Process a Kafka message once. Skips when eventId is missing or already processed.
   *
   * @return true when the handler ran, false when skipped
   */
  @Transactional
  public boolean processIfNew(Map<String, Object> event, Consumer<Map<String, Object>> handler) {
    UUID eventId = parseUuid(event.get("eventId"));
    if (eventId == null) {
      return false;
    }
    String eventType = stringValue(event.get("eventType"));
    if (eventType == null) {
      return false;
    }
    if (processedEventRepository.existsById(eventId)) {
      return false;
    }
    handler.accept(event);
    processedEventRepository.save(new ProcessedEventEntity(eventId, eventType, Instant.now()));
    return true;
  }

  private static UUID parseUuid(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof UUID uuid) {
      return uuid;
    }
    try {
      return UUID.fromString(value.toString());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String stringValue(Object value) {
    return value != null ? value.toString() : null;
  }
}
