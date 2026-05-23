package io.pravah.scheduler.application;

import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.infrastructure.persistence.entity.KafkaTriggerProcessedEntity;
import io.pravah.scheduler.infrastructure.persistence.repository.KafkaTriggerProcessedRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional Kafka trigger consumption with claim-first deduplication. */
@Service
public class KafkaTriggerConsumerService {

  private final KafkaTriggerProcessedRepository processedRepository;
  private final PipelineTriggerDispatchService dispatchService;

  public KafkaTriggerConsumerService(
      KafkaTriggerProcessedRepository processedRepository,
      PipelineTriggerDispatchService dispatchService) {
    this.processedRepository = processedRepository;
    this.dispatchService = dispatchService;
  }

  @Transactional
  public void processTriggerMessage(
      PipelineTrigger trigger,
      String topic,
      int partition,
      long offset,
      Map<String, Object> payload,
      String idempotencyKey) {
    if (processedRepository.existsByTriggerIdAndTopicAndPartitionNumAndOffsetNum(
        trigger.getId(), topic, partition, offset)) {
      return;
    }

    processedRepository.save(
        new KafkaTriggerProcessedEntity(trigger.getId(), topic, partition, offset, Instant.now()));

    try {
      dispatchService.dispatch(trigger, payload, idempotencyKey);
    } catch (RuntimeException ex) {
      processedRepository.deleteByTriggerIdAndTopicAndPartitionNumAndOffsetNum(
          trigger.getId(), topic, partition, offset);
      throw ex;
    }
  }
}
