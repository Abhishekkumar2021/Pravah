package io.pravah.scheduler.infrastructure.kafka;

import io.pravah.scheduler.application.TriggerConfigSupport;
import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.domain.model.TriggerType;
import io.pravah.scheduler.domain.repository.PipelineTriggerRepository;
import io.pravah.scheduler.infrastructure.persistence.TriggerRlsHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** In-memory index of active Kafka triggers by topic. */
@Component
public class KafkaTriggerRegistry {

  private volatile Map<String, List<PipelineTrigger>> triggersByTopic = Map.of();

  private final PipelineTriggerRepository triggerRepository;
  private final TriggerRlsHelper triggerRlsHelper;

  public KafkaTriggerRegistry(
      PipelineTriggerRepository triggerRepository, TriggerRlsHelper triggerRlsHelper) {
    this.triggerRepository = triggerRepository;
    this.triggerRlsHelper = triggerRlsHelper;
  }

  public void refresh() {
    triggerRlsHelper.enableKafkaConsumer();
    try {
      List<PipelineTrigger> triggers =
          triggerRepository.findByTriggerTypeAndEnabledTrue(TriggerType.KAFKA.value());
      Map<String, List<PipelineTrigger>> grouped = new LinkedHashMap<>();
      for (PipelineTrigger trigger : triggers) {
        Map<String, Object> config = TriggerConfigSupport.parseConfig(trigger.getConfig());
        String topic = TriggerConfigSupport.requireKafkaTopic(config);
        grouped.computeIfAbsent(topic, key -> new ArrayList<>()).add(trigger);
      }
      Map<String, List<PipelineTrigger>> immutable = new LinkedHashMap<>();
      grouped.forEach(
          (topic, list) -> immutable.put(topic, Collections.unmodifiableList(List.copyOf(list))));
      triggersByTopic = Collections.unmodifiableMap(immutable);
    } finally {
      triggerRlsHelper.disableKafkaConsumer();
    }
  }

  public Map<String, List<PipelineTrigger>> triggersByTopic() {
    return triggersByTopic;
  }

  public List<PipelineTrigger> triggersForTopic(String topic) {
    return triggersByTopic.getOrDefault(topic, List.of());
  }
}
