package io.pravah.scheduler.api.dto;

import java.time.Instant;
import java.util.List;

public record CronPreviewResponse(String description, List<Instant> nextRuns) {}
