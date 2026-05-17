package io.pravah.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectionCredentialResolverTest {

  private final ConnectionCredentialResolver resolver = new ConnectionCredentialResolver();

  @Test
  void resolvePassword_missingCredentials_returnsEmpty() {
    assertThat(resolver.resolvePassword(Map.of("host", "localhost"))).isEmpty();
    assertThat(resolver.resolvePassword(null)).isEmpty();
  }

  @Test
  void resolvePassword_blankPasswordReference_returnsEmpty() {
    Map<String, Object> config = Map.of("credentials", Map.of("password", "  "));
    assertThat(resolver.resolvePassword(config)).isEmpty();
  }

  @Test
  void resolveReference_unsetEnvVar_throws() {
    String varName = "PRAVAH_TEST_CONN_PW_" + System.nanoTime();
    assertThatThrownBy(() -> resolver.resolveReference("env:" + varName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not set")
        .hasMessageContaining(varName);
  }

  @Test
  void resolveReference_emptyEnvName_throws() {
    assertThatThrownBy(() -> resolver.resolveReference("env:"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported credential reference format");
  }

  @Test
  void resolveReference_vaultFormat_throwsUnsupportedUntilConfigured() {
    assertThatThrownBy(() -> resolver.resolveReference("vault:secret/data/db#password"))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Vault integration not configured");
  }

  @Test
  void resolveReference_unsupportedFormat_throws() {
    assertThatThrownBy(() -> resolver.resolveReference("unknown:something"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported credential reference format");
  }

  @Test
  void resolvePassword_invalidCredentialsType_throws() {
    Map<String, Object> config = Map.of("credentials", "not-an-object");
    assertThatThrownBy(() -> resolver.resolvePassword(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be an object");
  }
}
