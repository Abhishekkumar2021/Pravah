package io.pravah.pipeline.application;

/** JDBC connection material for stage execution (internal use only). */
public record ResolvedConnection(
    String name, String type, String jdbcUrl, String username, String password) {}
