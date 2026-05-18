package io.pravah.pipeline.api.dto;

/** Result of a JDBC connectivity check for a stored connection (US-12.16). */
public record TestConnectionResponse(boolean success, String message) {}
