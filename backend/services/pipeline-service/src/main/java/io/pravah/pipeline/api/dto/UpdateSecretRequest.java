package io.pravah.pipeline.api.dto;

import jakarta.validation.constraints.Size;

/** Request to update an existing tenant secret reference. */
public record UpdateSecretRequest(
    @Size(max = 500) String description,
    @Size(max = 50) String provider,
    @Size(max = 500) String providerPath) {}
