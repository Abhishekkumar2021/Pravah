package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageOutputSizeGuardTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void smallOutput_unchanged() {
    Map<String, Object> output = Map.of("row_count", 1, "executor", "sql");

    StageOutputSizeEnforcement result =
        StageOutputSizeGuard.enforce(output, 1024, 512, objectMapper);

    assertThat(result.output()).isEqualTo(output);
    assertThat(result.truncated()).isFalse();
    assertThat(result.exceededWarnThreshold()).isFalse();
    assertThat(result.serializationFailed()).isFalse();
  }

  @Test
  void warnThreshold_exceededWarnFlag() {
    Map<String, Object> output = Map.of("payload", "x".repeat(600));

    StageOutputSizeEnforcement result =
        StageOutputSizeGuard.enforce(output, 10_000, 500, objectMapper);

    assertThat(result.truncated()).isFalse();
    assertThat(result.exceededWarnThreshold()).isTrue();
    assertThat(result.serializedBytes()).isGreaterThan(500);
  }

  @Test
  void largeOutput_truncated() {
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("executor", "sql");
    output.put("blob", "x".repeat(2000));

    StageOutputSizeEnforcement result = StageOutputSizeGuard.enforce(output, 100, 50, objectMapper);

    assertThat(result.truncated()).isTrue();
    assertThat(result.output()).containsEntry("_truncated", true);
    assertThat(result.output()).containsKey("_original_bytes");
    assertThat(result.serializedBytes()).isGreaterThan(100);
  }
}
