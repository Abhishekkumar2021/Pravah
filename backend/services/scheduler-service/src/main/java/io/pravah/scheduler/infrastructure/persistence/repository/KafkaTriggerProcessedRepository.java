package io.pravah.scheduler.infrastructure.persistence.repository;

import io.pravah.scheduler.infrastructure.persistence.entity.KafkaTriggerProcessedEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for Kafka trigger idempotent consumer deduplication. */
@Repository
public interface KafkaTriggerProcessedRepository
    extends JpaRepository<KafkaTriggerProcessedEntity, UUID> {

  boolean existsByTriggerIdAndTopicAndPartitionNumAndOffsetNum(
      UUID triggerId, String topic, int partitionNum, long offsetNum);

  @Modifying
  @Query("DELETE FROM KafkaTriggerProcessedEntity e WHERE e.processedAt < :cutoff")
  int deleteOlderThan(Instant cutoff);
}
