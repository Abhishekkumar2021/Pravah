package io.pravah.scheduler.infrastructure.persistence.repository;

import io.pravah.scheduler.infrastructure.persistence.entity.TriggerDispatchHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriggerDispatchHistoryRepository
    extends JpaRepository<TriggerDispatchHistoryEntity, UUID> {

  List<TriggerDispatchHistoryEntity> findByTriggerIdOrderByCreatedAtDesc(UUID triggerId);
}
