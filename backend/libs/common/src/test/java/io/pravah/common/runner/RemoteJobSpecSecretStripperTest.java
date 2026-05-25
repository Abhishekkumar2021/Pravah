package io.pravah.common.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RemoteJobSpecSecretStripperTest {

  @Test
  void stripSecrets_movesSecretRefsOutOfEnvironment() {
    Map<String, Object> raw =
        Map.of("env", Map.of("API_KEY", "${secret.api_key}", "PLAIN", "hello"));
    RemoteJobSpecPayload payload =
        new RemoteJobSpecPayload(
            "container",
            "alpine:3.19",
            List.of("echo"),
            Map.of("API_KEY", "super-secret", "PLAIN", "hello"),
            60,
            null,
            null);

    RemoteJobSpecPayload stripped = RemoteJobSpecSecretStripper.stripSecrets(payload, raw);

    assertThat(stripped.environment()).containsEntry("PLAIN", "hello");
    assertThat(stripped.environment()).doesNotContainKey("API_KEY");
    assertThat(stripped.secretEnvironment()).containsEntry("API_KEY", "api_key");
  }

  @Test
  void stripSecrets_stripsSqlPassword() {
    RemoteJobSpecPayload payload =
        new RemoteJobSpecPayload(
            "sql", null, List.of(), Map.of(RemoteJobSpecEnv.SQL_PASSWORD, "pw"), 60, null, null);

    RemoteJobSpecPayload stripped = RemoteJobSpecSecretStripper.stripSecrets(payload, Map.of());

    assertThat(stripped.environment()).doesNotContainKey(RemoteJobSpecEnv.SQL_PASSWORD);
    assertThat(stripped.secretEnvironment())
        .containsEntry(
            RemoteJobSpecEnv.SQL_PASSWORD, RemoteJobSpecSecretStripper.RUNTIME_SQL_PASSWORD);
  }
}
