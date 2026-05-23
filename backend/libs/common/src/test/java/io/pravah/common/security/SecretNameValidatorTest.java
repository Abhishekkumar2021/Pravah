package io.pravah.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecretNameValidatorTest {

  @Test
  void acceptsValidTenantSecretName() {
    assertThatCode(() -> SecretNameValidator.requireValidTenantSecretName("my_secret"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsReservedPrefix() {
    assertThatThrownBy(() -> SecretNameValidator.requireValidTenantSecretName("__internal"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Reserved");
  }

  @Test
  void rejectsInvalidCharacters() {
    assertThatThrownBy(() -> SecretNameValidator.requireValidTenantSecretName("bad name"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid secret name");
  }
}
