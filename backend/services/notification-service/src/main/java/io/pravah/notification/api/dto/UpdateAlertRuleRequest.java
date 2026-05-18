package io.pravah.notification.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateAlertRuleRequest(
    @Size(max = 255) String name,
    @Size(max = 2000) String description,
    Boolean enabled,
    String conditions,
    String channels,
    Integer dedupWindowSeconds) {}
