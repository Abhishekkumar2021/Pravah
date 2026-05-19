package io.pravah.connect.connector.file;

import io.pravah.connect.connector.SinkConnector;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;

import java.util.List;
import java.util.Map;

/**
 * Base class for file-based connectors (S3, GCS, Azure Blob, Local FS, etc.).
 */
public abstract class AbstractFileConnector implements SourceConnector, SinkConnector {

    protected abstract String getConnectorId();

    protected abstract String getDisplayName();

    protected abstract String getDescription();

    protected abstract String getIconName();

    protected abstract List<io.pravah.connect.domain.ConfigField> getConfigFields();

    protected abstract List<String> getTags();

    @Override
    public ConnectorSpec getSpec() {
        return ConnectorSpec.builder(getConnectorId())
                .name(getDisplayName())
                .description(getDescription())
                .icon(getIconName())
                .category("File Storage")
                .type(ConnectorType.FILE)
                .mode(ConnectorMode.BIDIRECTIONAL)
                .configFields(getConfigFields())
                .capabilities(Map.of(
                        "discover", true,
                        "incremental", true,
                        "fullRefresh", true,
                        "formats", List.of("csv", "json", "parquet", "avro", "excel")
                ))
                .tags(getTags())
                .build();
    }

    /**
     * Supported file formats.
     */
    public enum FileFormat {
        CSV,
        JSON,
        JSONL,
        PARQUET,
        AVRO,
        EXCEL,
        XML
    }

    protected static boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }

    protected static String getString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }

    protected static int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }

    protected static boolean getBoolean(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
