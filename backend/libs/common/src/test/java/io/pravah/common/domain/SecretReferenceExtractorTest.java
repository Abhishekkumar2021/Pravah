package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecretReferenceExtractorTest {

  @Test
  void extract_noSecrets_returnsEmpty() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(Map.of("id", "extract", "config", Map.of("query", "SELECT * FROM t"))));
    Set<String> secrets = SecretReferenceExtractor.extract(definition);
    assertThat(secrets).isEmpty();
  }

  @Test
  void extract_secretInStageConfig() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "call_api",
                    "config",
                    Map.of(
                        "url",
                        "https://api.example.com",
                        "headers",
                        Map.of("Authorization", "Bearer ${secret.api_key}")))));
    Set<String> secrets = SecretReferenceExtractor.extract(definition);
    assertThat(secrets).containsExactly("api_key");
  }

  @Test
  void extract_multipleSecrets() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "stage1", "config", Map.of("key1", "${secret.first_secret}")),
                Map.of("id", "stage2", "config", Map.of("key2", "${secret.second_secret}"))));
    Set<String> secrets = SecretReferenceExtractor.extract(definition);
    assertThat(secrets).containsExactlyInAnyOrder("first_secret", "second_secret");
  }

  @Test
  void extract_duplicateSecrets_returnsUnique() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "s1", "config", Map.of("a", "${secret.same}")),
                Map.of("id", "s2", "config", Map.of("b", "${secret.same}"))));
    Set<String> secrets = SecretReferenceExtractor.extract(definition);
    assertThat(secrets).containsExactly("same");
  }

  @Test
  void extract_ignoresVariablesBlock() {
    Map<String, Object> definition =
        Map.of(
            "variables",
            Map.of("my_var", Map.of("type", "string", "default", "${secret.should_not_extract}")),
            "stages",
            List.of(Map.of("id", "s1", "config", Map.of("key", "${secret.real_secret}"))));
    Set<String> secrets = SecretReferenceExtractor.extract(definition);
    assertThat(secrets).containsExactly("real_secret");
    assertThat(secrets).doesNotContain("should_not_extract");
  }

  @Test
  void extract_ignoresEnvironmentsBlock() {
    Map<String, Object> definition =
        Map.of(
            "environments",
            Map.of("prod", Map.of("db_pass", "${secret.env_secret}")),
            "stages",
            List.of(Map.of("id", "s1", "config", Map.of("key", "${secret.stage_secret}"))));
    Set<String> secrets = SecretReferenceExtractor.extract(definition);
    assertThat(secrets).containsExactly("stage_secret");
    assertThat(secrets).doesNotContain("env_secret");
  }

  @Test
  void extract_deeplyNestedSecrets() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "nested",
                    "config",
                    Map.of(
                        "level1",
                        Map.of(
                            "level2",
                            Map.of("level3", Map.of("deep", "${secret.deep_secret}")))))));
    Set<String> secrets = SecretReferenceExtractor.extract(definition);
    assertThat(secrets).containsExactly("deep_secret");
  }

  @Test
  void extract_secretsInArrays() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of(
                    "id",
                    "multi",
                    "config",
                    Map.of("keys", List.of("${secret.key1}", "literal", "${secret.key2}")))));
    Set<String> secrets = SecretReferenceExtractor.extract(definition);
    assertThat(secrets).containsExactlyInAnyOrder("key1", "key2");
  }

  @Test
  void extract_nullDefinition_returnsEmpty() {
    assertThat(SecretReferenceExtractor.extract(null)).isEmpty();
  }
}
