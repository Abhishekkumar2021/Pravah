package io.pravah.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveLogSanitizerTest {

  @Test
  void redactEnvironment_masksSensitiveKeys() {
    Map<String, String> redacted =
        SensitiveLogSanitizer.redactEnvironment(
            Map.of("SQL_PASSWORD", "secret", "STAGE_ID", "extract"));

    assertThat(redacted.get("SQL_PASSWORD")).isEqualTo("***");
    assertThat(redacted.get("STAGE_ID")).isEqualTo("extract");
  }
}
