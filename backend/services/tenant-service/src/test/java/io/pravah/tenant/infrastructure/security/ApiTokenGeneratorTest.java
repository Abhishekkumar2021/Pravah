package io.pravah.tenant.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiTokenGeneratorTest {

  @Test
  void generate_hasExpectedPrefixAndLength() {
    String token = ApiTokenGenerator.generate();
    assertThat(token).startsWith(ApiTokenGenerator.PREFIX);
    assertThat(token).hasSize(ApiTokenGenerator.PREFIX.length() + 64);
  }

  @Test
  void isApiToken_detectsPrefix() {
    assertThat(ApiTokenGenerator.isApiToken("prv_1_abc")).isTrue();
    assertThat(ApiTokenGenerator.isApiToken("eyJhbGciOiJSUzI1NiJ9")).isFalse();
  }
}
