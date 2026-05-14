package io.pravah.execution.infrastructure.persistence.repository;

import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionEntityRepository extends JpaRepository<ExecutionEntity, UUID> {}
