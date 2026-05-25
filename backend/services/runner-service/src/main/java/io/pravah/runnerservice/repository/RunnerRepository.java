package io.pravah.runnerservice.repository;

import io.pravah.runnerservice.domain.Runner;
import io.pravah.runnerservice.domain.RunnerStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for Runner entities. */
@Repository
public interface RunnerRepository extends JpaRepository<Runner, UUID> {

  List<Runner> findByTenantId(UUID tenantId);

  List<Runner> findByTenantIdAndStatus(UUID tenantId, RunnerStatus status);

  Optional<Runner> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<Runner> findByTenantIdAndName(UUID tenantId, String name);

  @Query("SELECT r FROM Runner r WHERE r.tenantId = :tenantId AND r.status IN (:statuses)")
  List<Runner> findByTenantIdAndStatusIn(
      @Param("tenantId") UUID tenantId, @Param("statuses") List<RunnerStatus> statuses);

  @Query(
      "SELECT r FROM Runner r WHERE r.tenantId = :tenantId "
          + "AND r.status = 'ONLINE' AND r.activeJobs < r.maxConcurrentJobs "
          + "ORDER BY r.activeJobs ASC")
  List<Runner> findAvailableRunners(@Param("tenantId") UUID tenantId);

  @Query(
      "SELECT r FROM Runner r WHERE r.status IN ('ONLINE', 'BUSY') "
          + "AND r.lastHeartbeatAt < :cutoff")
  List<Runner> findStaleRunners(@Param("cutoff") Instant cutoff);

  long countByTenantId(UUID tenantId);

  long countByTenantIdAndStatus(UUID tenantId, RunnerStatus status);
}
