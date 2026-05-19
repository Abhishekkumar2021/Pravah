package io.pravah.runnerservice.repository;

import io.pravah.runnerservice.domain.JobAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobAssignmentRepository extends JpaRepository<JobAssignment, UUID> {

  Optional<JobAssignment> findByJobId(UUID jobId);

  List<JobAssignment> findByRunnerId(UUID runnerId);

  List<JobAssignment> findByExecutionId(UUID executionId);
}
