package io.pravah.common.net;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UrlSafetyValidatorTest {

  @Test
  void rejectsLoopbackHttpUrl() {
    assertThatThrownBy(
            () -> UrlSafetyValidator.validateHttpUrlForOutboundRequest("http://127.0.0.1/hook"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allowsPublicHttpsUrl() {
    assertThatCode(
            () -> UrlSafetyValidator.validateHttpUrlForOutboundRequest("https://example.com/hook"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsPrivateJdbcHost() {
    assertThatThrownBy(
            () -> UrlSafetyValidator.validateJdbcTarget("jdbc:postgresql://10.0.0.5:5432/db"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allowsPublicJdbcHost() {
    assertThatCode(
            () -> UrlSafetyValidator.validateJdbcTarget("jdbc:postgresql://8.8.8.8:5432/app"))
        .doesNotThrowAnyException();
  }
}
