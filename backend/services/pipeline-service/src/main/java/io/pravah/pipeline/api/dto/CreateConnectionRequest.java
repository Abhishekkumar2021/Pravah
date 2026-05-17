package io.pravah.pipeline.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Request to create a named connection.
 *
 * <p>Credential references are stored inside {@code config.credentials}. Example:
 *
 * <pre>{@code
 * {
 *   "name": "warehouse",
 *   "type": "postgres",
 *   "config": {
 *     "host": "localhost",
 *     "port": 5432,
 *     "database": "pravah",
 *     "username": "pravah",
 *     "credentials": {
 *       "password": "env:PRAVAH_DB_PASSWORD"
 *     }
 *   }
 * }
 * }</pre>
 *
 * Supported credential reference formats:
 *
 * <ul>
 *   <li>{@code env:VAR_NAME} — reads from environment variable
 *   <li>(future) {@code vault:secret/path#key} — reads from HashiCorp Vault
 * </ul>
 */
public record CreateConnectionRequest(
    @NotBlank
        @Size(max = 255)
        @Pattern(
            regexp = "^[a-z][a-z0-9_]*$",
            message = "name must be lowercase alphanumeric with underscores")
        String name,
    @NotBlank @Size(max = 100) String type,
    @NotNull Map<String, Object> config) {}
