package io.pravah.common.domain;

import java.util.Map;

/**
 * Result of applying stage output size limits (US-02.10).
 *
 * @param output output safe to persist (possibly truncated)
 * @param serializedBytes measured JSON size in bytes, or {@link #SERIALIZATION_FAILED} if
 *     measurement failed
 * @param truncated whether output was replaced with a truncated summary
 * @param exceededWarnThreshold whether output exceeded the warn threshold but not max
 * @param serializationFailed whether JSON serialization failed during measurement
 */
public record StageOutputSizeEnforcement(
    Map<String, Object> output,
    int serializedBytes,
    boolean truncated,
    boolean exceededWarnThreshold,
    boolean serializationFailed) {

  /** Sentinel when output cannot be serialized for size measurement. */
  public static final int SERIALIZATION_FAILED = -1;
}
