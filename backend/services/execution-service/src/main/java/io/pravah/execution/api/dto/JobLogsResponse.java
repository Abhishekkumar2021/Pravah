package io.pravah.execution.api.dto;

import java.util.List;
import java.util.UUID;

public record JobLogsResponse(UUID jobId, UUID executionId, List<JobLogLineResponse> lines) {}
