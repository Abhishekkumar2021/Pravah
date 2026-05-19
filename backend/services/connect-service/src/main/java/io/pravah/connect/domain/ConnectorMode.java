package io.pravah.connect.domain;

/** Indicates whether a connector can read, write, or both. */
public enum ConnectorMode {
  SOURCE,
  SINK,
  BIDIRECTIONAL
}
