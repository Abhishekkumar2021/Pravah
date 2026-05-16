package io.pravah.tenant.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiTokenHasherTest {

  @Test
  void hash_isDeterministic() {
    String raw = "prv_1_abc123";
    assertThat(ApiTokenHasher.hash(raw)).isEqualTo(ApiTokenHasher.hash(raw));
  }

  @Test
  void hash_produces64HexChars() {
    String hash = ApiTokenHasher.hash("prv_1_test");
    assertThat(hash).hasSize(64).matches("[0-9a-f]+");
  }
}
