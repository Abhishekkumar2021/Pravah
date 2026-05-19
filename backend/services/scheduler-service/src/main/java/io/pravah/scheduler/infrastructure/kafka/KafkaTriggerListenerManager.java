package io.pravah.scheduler.infrastructure.kafka;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.scheduler.application.PipelineTriggerDispatchService;
import io.pravah.scheduler.application.TriggerConfigSupport;
import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.infrastructure.persistence.entity.KafkaTriggerProcessedEntity;
import io.pravah.scheduler.infrastructure.persistence.repository.KafkaTriggerProcessedRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KafkaTriggerListenerManager {

  private static final Logger log = LoggerFactory.getLogger(KafkaTriggerListenerManager.class);

  private final ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory;
  private final KafkaTriggerRegistry registry;
  private final PipelineTriggerDispatchService dispatchService;
  private final KafkaTriggerProcessedRepository processedRepository;

  private final Map<String, ConcurrentMessageListenerContainer<String, Map<String, Object>>>
      containers = new ConcurrentHashMap<>();

  public KafkaTriggerListenerManager(
      ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory,
      KafkaTriggerRegistry registry,
      PipelineTriggerDispatchService dispatchService,
      KafkaTriggerProcessedRepository processedRepository) {
    this.factory = factory;
    this.registry = registry;
    this.dispatchService = dispatchService;
    this.processedRepository = processedRepository;
  }

  @PostConstruct
  void start() {
    refresh();
  }

  @PreDestroy
  void stopAll() {
    containers.values().forEach(ConcurrentMessageListenerContainer::stop);
    containers.clear();
  }

  @EventListener
  public void onTriggersChanged(TriggersChangedEvent event) {
    refresh();
  }

  public synchronized void refresh() {
    registry.refresh();
    Set<String> desiredTopics = new HashSet<>(registry.triggersByTopic().keySet());

    Set<String> existing = new HashSet<>(containers.keySet());
    for (String topic : existing) {
      if (!desiredTopics.contains(topic)) {
        ConcurrentMessageListenerContainer<String, Map<String, Object>> container =
            containers.remove(topic);
        if (container != null) {
          container.stop();
          log.info("Stopped Kafka trigger listener", kv("topic", topic));
        }
      }
    }

    for (String topic : desiredTopics) {
      containers.computeIfAbsent(
          topic,
          t -> {
            ConcurrentMessageListenerContainer<String, Map<String, Object>> container =
                factory.createContainer(t);
            container.setupMessageListener(
                (MessageListener<String, Map<String, Object>>) this::onMessage);
            container.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
            container.start();
            log.info("Started Kafka trigger listener", kv("topic", t));
            return container;
          });
    }
  }

  void onMessage(ConsumerRecord<String, Map<String, Object>> record) {
    String topic = record.topic();
    int partition = record.partition();
    long offset = record.offset();
    Map<String, Object> payload = record.value() != null ? record.value() : Map.of();
    List<PipelineTrigger> triggers = registry.triggersForTopic(topic);

    for (PipelineTrigger trigger : triggers) {
      Map<String, Object> config = TriggerConfigSupport.parseConfig(trigger.getConfig());
      Map<String, Object> filter = TriggerConfigSupport.kafkaFilter(config);
      if (!TriggerConfigSupport.matchesKafkaFilter(filter, payload)) {
        continue;
      }

      if (isAlreadyProcessed(trigger.getId(), topic, partition, offset)) {
        log.debug(
            "Skipping duplicate Kafka message",
            kv("trigger_id", trigger.getId()),
            kv("topic", topic),
            kv("partition", partition),
            kv("offset", offset));
        continue;
      }

      try {
        dispatchService.dispatch(trigger, payload);
        markProcessed(trigger.getId(), topic, partition, offset);
      } catch (Exception e) {
        log.warn(
            "Kafka trigger dispatch failed",
            kv("trigger_id", trigger.getId()),
            kv("topic", topic),
            kv("partition", partition),
            kv("offset", offset),
            kv("error", e.getMessage()));
      }
    }
  }

  private boolean isAlreadyProcessed(UUID triggerId, String topic, int partition, long offset) {
    return processedRepository.existsByTriggerIdAndTopicAndPartitionNumAndOffsetNum(
        triggerId, topic, partition, offset);
  }

  @Transactional
  void markProcessed(UUID triggerId, String topic, int partition, long offset) {
    processedRepository.save(
        new KafkaTriggerProcessedEntity(triggerId, topic, partition, offset, Instant.now()));
  }

  @Scheduled(cron = "0 0 3 * * ?")
  @Transactional
  void cleanupOldProcessedRecords() {
    Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
    int deleted = processedRepository.deleteOlderThan(cutoff);
    if (deleted > 0) {
      log.info("Cleaned up old Kafka trigger processed records", kv("deleted", deleted));
    }
  }

  public record TriggersChangedEvent() {}
}
