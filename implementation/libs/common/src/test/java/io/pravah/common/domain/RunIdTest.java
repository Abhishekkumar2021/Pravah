package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RunId")
class RunIdTest {

  @Nested
  @DisplayName("Constructor validation")
  class ConstructorValidation {

    @Test
    @DisplayName("should reject null UUID")
    void shouldRejectNullUuid() {
      assertThatThrownBy(() -> new RunId(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("should accept valid UUID")
    void shouldAcceptValidUuid() {
      UUID uuid = UUID.randomUUID();
      RunId id = new RunId(uuid);

      assertThat(id.value()).isEqualTo(uuid);
    }
  }

  @Nested
  @DisplayName("Factory methods")
  class FactoryMethods {

    @Test
    @DisplayName("of(UUID) should create RunId")
    void ofUuidShouldCreateRunId() {
      UUID uuid = UUID.randomUUID();
      RunId id = RunId.of(uuid);

      assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("of(UUID) should reject null")
    void ofUuidShouldRejectNull() {
      assertThatThrownBy(() -> RunId.of((UUID) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String) should create RunId from valid UUID string")
    void ofStringShouldCreateRunId() {
      UUID uuid = UUID.randomUUID();
      RunId id = RunId.of(uuid.toString());

      assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("of(String) should reject null")
    void ofStringShouldRejectNull() {
      assertThatThrownBy(() -> RunId.of((String) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String) should reject invalid UUID string")
    void ofStringShouldRejectInvalidUuidString() {
      assertThatThrownBy(() -> RunId.of("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generate() should create unique RunId")
    void generateShouldCreateUniqueRunId() {
      RunId id1 = RunId.generate();
      RunId id2 = RunId.generate();

      assertThat(id1).isNotEqualTo(id2);
      assertThat(id1.value()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Equality")
  class Equality {

    @Test
    @DisplayName("should be equal when UUIDs match")
    void shouldBeEqualWhenUuidsMatch() {
      UUID uuid = UUID.randomUUID();
      RunId id1 = RunId.of(uuid);
      RunId id2 = RunId.of(uuid);

      assertThat(id1).isEqualTo(id2);
      assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    @DisplayName("should not be equal when UUIDs differ")
    void shouldNotBeEqualWhenUuidsDiffer() {
      RunId id1 = RunId.generate();
      RunId id2 = RunId.generate();

      assertThat(id1).isNotEqualTo(id2);
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTest {

    @Test
    @DisplayName("should return UUID string representation")
    void shouldReturnUuidStringRepresentation() {
      UUID uuid = UUID.randomUUID();
      RunId id = RunId.of(uuid);

      assertThat(id.toString()).isEqualTo(uuid.toString());
    }
  }
}
