package io.pravah.scheduler.api.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdatePipelineTriggerRequest(
    @Size(max = 255) String name, Map<String, Object> config, Boolean enabled) {}
