package io.pravah.pipeline.api.dto;

import java.util.Map;

/**
 * Request to update an existing connection's configuration.
 *
 * <p>Credentials are embedded inside {@code config.credentials}. See {@link
 * CreateConnectionRequest} for the full format documentation.
 */
public record UpdateConnectionRequest(Map<String, Object> config) {}
