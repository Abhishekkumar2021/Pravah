package io.pravah.connect.domain;

/**
 * Categories of data connectors supported by the platform.
 */
public enum ConnectorType {
    DATABASE,
    FILE,
    PROTOCOL,
    STREAMING,
    SAAS,
    CDC
}
