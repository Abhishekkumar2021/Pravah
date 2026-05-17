package io.pravah.execution.application.port;

/** JDBC settings resolved from a named platform connection. */
public record ResolvedJdbcConnection(
    String name, String jdbcUrl, String username, String password) {}
