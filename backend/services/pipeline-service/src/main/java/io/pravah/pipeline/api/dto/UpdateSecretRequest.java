package io.pravah.pipeline.api.dto;

import jakarta.validation.constraints.Size;

/** Request to update an existing tenant secret reference or re-encrypt a stored value. */
public record UpdateSecretRequest(
    @Size(max = 500) String description,
    @Size(max = 50) String provider,
    @Size(max = 500) String providerPath,
    @Size(max = 65536) String value) {}
