package io.pravah.notification.infrastructure.persistence.repository;

import io.pravah.notification.infrastructure.persistence.entity.ProcessedEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {}
