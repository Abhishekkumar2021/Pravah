package io.pravah.runnerservice.api;

import io.pravah.runnerservice.domain.Runner;
import io.pravah.runnerservice.domain.RunnerStatus;
import io.pravah.runnerservice.service.RunnerConnectionManager;
import io.pravah.runnerservice.service.RunnerService;
import io.pravah.spring.multitenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for runner management (admin operations).
 */
@RestController
@RequestMapping("/api/v1/runners")
@Tag(name = "Runners", description = "Runner fleet management")
public class RunnerController {

    private final RunnerService runnerService;
    private final RunnerConnectionManager connectionManager;

    public RunnerController(RunnerService runnerService, RunnerConnectionManager connectionManager) {
        this.runnerService = runnerService;
        this.connectionManager = connectionManager;
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return tenantId;
    }

    @Operation(summary = "List all runners")
    @GetMapping
    public ResponseEntity<List<RunnerDto>> listRunners(
            @RequestParam(required = false) RunnerStatus status
    ) {
        UUID tenantId = requireTenantId();
        List<Runner> runners;

        if (status != null) {
            runners = runnerService.listRunnersByStatus(tenantId, status);
        } else {
            runners = runnerService.listRunners(tenantId);
        }

        return ResponseEntity.ok(runners.stream().map(this::toDto).toList());
    }

    @Operation(summary = "Get runner by ID")
    @GetMapping("/{runnerId}")
    public ResponseEntity<RunnerDto> getRunner(@PathVariable UUID runnerId) {
        UUID tenantId = requireTenantId();
        return runnerService.getRunner(tenantId, runnerId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a runner")
    @DeleteMapping("/{runnerId}")
    public ResponseEntity<Void> deleteRunner(@PathVariable UUID runnerId) {
        UUID tenantId = requireTenantId();
        runnerService.deleteRunner(tenantId, runnerId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get fleet statistics")
    @GetMapping("/stats")
    public ResponseEntity<FleetStats> getFleetStats() {
        UUID tenantId = requireTenantId();
        List<Runner> runners = runnerService.listRunners(tenantId);

        long total = runners.size();
        long online = runners.stream().filter(r -> r.getStatus() == RunnerStatus.ONLINE).count();
        long busy = runners.stream().filter(r -> r.getStatus() == RunnerStatus.BUSY).count();
        long offline = runners.stream().filter(r -> r.getStatus() == RunnerStatus.OFFLINE).count();
        int totalCapacity = runners.stream().mapToInt(Runner::getMaxConcurrentJobs).sum();
        int activeJobs = runners.stream().mapToInt(Runner::getActiveJobs).sum();

        return ResponseEntity.ok(new FleetStats(
                total, online, busy, offline, totalCapacity, activeJobs,
                connectionManager.getActiveConnectionCount()
        ));
    }

    private RunnerDto toDto(Runner runner) {
        return new RunnerDto(
                runner.getId(),
                runner.getName(),
                runner.getVersion(),
                runner.getStatus().name(),
                runner.getMaxConcurrentJobs(),
                runner.getActiveJobs(),
                runner.getLabels(),
                runner.getSupportedExecutors(),
                runner.getLastHeartbeatAt() != null ? runner.getLastHeartbeatAt().toString() : null,
                runner.getRegisteredAt().toString(),
                connectionManager.isConnected(runner.getId()),
                runner.getLastMetricsCpuPercent(),
                runner.getLastMetricsMemoryUsedBytes(),
                runner.getLastMetricsDiskAvailableBytes()
        );
    }

    public record RunnerDto(
            UUID id,
            String name,
            String version,
            String status,
            int maxConcurrentJobs,
            int activeJobs,
            java.util.Map<String, String> labels,
            String supportedExecutors,
            String lastHeartbeatAt,
            String registeredAt,
            boolean connected,
            double cpuUsagePercent,
            long memoryUsedBytes,
            long diskAvailableBytes
    ) {}

    public record FleetStats(
            long totalRunners,
            long onlineRunners,
            long busyRunners,
            long offlineRunners,
            int totalCapacity,
            int activeJobs,
            int activeConnections
    ) {}
}
