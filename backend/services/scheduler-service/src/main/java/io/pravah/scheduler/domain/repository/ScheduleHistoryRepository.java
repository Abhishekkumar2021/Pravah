package io.pravah.scheduler.domain.repository;

import io.pravah.scheduler.domain.model.ScheduleHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleHistoryRepository extends JpaRepository<ScheduleHistory, UUID> {}
