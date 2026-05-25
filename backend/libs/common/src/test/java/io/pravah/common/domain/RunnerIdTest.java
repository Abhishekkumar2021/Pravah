package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RunnerId")
class RunnerIdTest {

  @Nested
  @DisplayName("Constructor validation")
  class ConstructorValidation {

    @Test
    @DisplayName("should reject null UUID")
    void shouldRejectNullUuid() {
      assertThatThrownBy(() -> new RunnerId(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("should accept valid UUID")
    void shouldAcceptValidUuid() {
      UUID uuid = UUID.randomUUID();
      RunnerId id = new RunnerId(uuid);

      assertThat(id.value()).isEqualTo(uuid);
    }
  }

  @Nested
  @DisplayName("Factory methods")
  class FactoryMethods {

    @Test
    @DisplayName("of(UUID) should create RunnerId")
    void ofUuidShouldCreateRunnerId() {
      UUID uuid = UUID.randomUUID();
      RunnerId id = RunnerId.of(uuid);

      assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("of(UUID) should reject null")
    void ofUuidShouldRejectNull() {
      assertThatThrownBy(() -> RunnerId.of((UUID) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String) should create RunnerId from valid UUID string")
    void ofStringShouldCreateRunnerId() {
      UUID uuid = UUID.randomUUID();
      RunnerId id = RunnerId.of(uuid.toString());

      assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("of(String) should reject null")
    void ofStringShouldRejectNull() {
      assertThatThrownBy(() -> RunnerId.of((String) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String) should reject invalid UUID string")
    void ofStringShouldRejectInvalidUuidString() {
      assertThatThrownBy(() -> RunnerId.of("not-a-uuid"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generate() should create unique RunnerId")
    void generateShouldCreateUniqueRunnerId() {
      RunnerId id1 = RunnerId.generate();
      RunnerId id2 = RunnerId.generate();

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
      RunnerId id1 = RunnerId.of(uuid);
      RunnerId id2 = RunnerId.of(uuid);

      assertThat(id1).isEqualTo(id2);
      assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    @DisplayName("should not be equal when UUIDs differ")
    void shouldNotBeEqualWhenUuidsDiffer() {
      RunnerId id1 = RunnerId.generate();
      RunnerId id2 = RunnerId.generate();

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
      RunnerId id = RunnerId.of(uuid);

      assertThat(id.toString()).isEqualTo(uuid.toString());
    }
  }
}
