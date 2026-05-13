package io.pravah.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserId")
class UserIdTest {

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject null UUID")
        void shouldRejectNullUuid() {
            assertThatThrownBy(() -> new UserId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("null");
        }

        @Test
        @DisplayName("should accept valid UUID")
        void shouldAcceptValidUuid() {
            UUID uuid = UUID.randomUUID();
            UserId id = new UserId(uuid);

            assertThat(id.value()).isEqualTo(uuid);
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("of(UUID) should create UserId")
        void ofUuidShouldCreateUserId() {
            UUID uuid = UUID.randomUUID();
            UserId id = UserId.of(uuid);

            assertThat(id.value()).isEqualTo(uuid);
        }

        @Test
        @DisplayName("of(UUID) should reject null")
        void ofUuidShouldRejectNull() {
            assertThatThrownBy(() -> UserId.of((UUID) null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String) should create UserId from valid UUID string")
        void ofStringShouldCreateUserId() {
            UUID uuid = UUID.randomUUID();
            UserId id = UserId.of(uuid.toString());

            assertThat(id.value()).isEqualTo(uuid);
        }

        @Test
        @DisplayName("of(String) should reject null")
        void ofStringShouldRejectNull() {
            assertThatThrownBy(() -> UserId.of((String) null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String) should reject invalid UUID string")
        void ofStringShouldRejectInvalidUuidString() {
            assertThatThrownBy(() -> UserId.of("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("generate() should create unique UserId")
        void generateShouldCreateUniqueUserId() {
            UserId id1 = UserId.generate();
            UserId id2 = UserId.generate();

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
            UserId id1 = UserId.of(uuid);
            UserId id2 = UserId.of(uuid);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when UUIDs differ")
        void shouldNotBeEqualWhenUuidsDiffer() {
            UserId id1 = UserId.generate();
            UserId id2 = UserId.generate();

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
            UserId id = UserId.of(uuid);

            assertThat(id.toString()).isEqualTo(uuid.toString());
        }
    }
}
