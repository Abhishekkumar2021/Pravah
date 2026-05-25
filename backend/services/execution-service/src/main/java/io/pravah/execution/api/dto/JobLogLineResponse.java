package io.pravah.execution.api.dto;

import java.time.Instant;
import java.util.UUID;

public record JobLogLineResponse(UUID id, Instant logTime, String level, String message) {}
