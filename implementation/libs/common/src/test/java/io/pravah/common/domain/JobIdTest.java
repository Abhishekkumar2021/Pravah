package io.pravah.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobId")
class JobIdTest {

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject null UUID")
        void shouldRejectNullUuid() {
            assertThatThrownBy(() -> new JobId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("null");
        }

        @Test
        @DisplayName("should accept valid UUID")
        void shouldAcceptValidUuid() {
            UUID uuid = UUID.randomUUID();
            JobId id = new JobId(uuid);

            assertThat(id.value()).isEqualTo(uuid);
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("of(UUID) should create JobId")
        void ofUuidShouldCreateJobId() {
            UUID uuid = UUID.randomUUID();
            JobId id = JobId.of(uuid);

            assertThat(id.value()).isEqualTo(uuid);
        }

        @Test
        @DisplayName("of(UUID) should reject null")
        void ofUuidShouldRejectNull() {
            assertThatThrownBy(() -> JobId.of((UUID) null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String) should create JobId from valid UUID string")
        void ofStringShouldCreateJobId() {
            UUID uuid = UUID.randomUUID();
            JobId id = JobId.of(uuid.toString());

            assertThat(id.value()).isEqualTo(uuid);
        }

        @Test
        @DisplayName("of(String) should reject null")
        void ofStringShouldRejectNull() {
            assertThatThrownBy(() -> JobId.of((String) null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of(String) should reject invalid UUID string")
        void ofStringShouldRejectInvalidUuidString() {
            assertThatThrownBy(() -> JobId.of("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("generate() should create unique JobId")
        void generateShouldCreateUniqueJobId() {
            JobId id1 = JobId.generate();
            JobId id2 = JobId.generate();

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
            JobId id1 = JobId.of(uuid);
            JobId id2 = JobId.of(uuid);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when UUIDs differ")
        void shouldNotBeEqualWhenUuidsDiffer() {
            JobId id1 = JobId.generate();
            JobId id2 = JobId.generate();

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
            JobId id = JobId.of(uuid);

            assertThat(id.toString()).isEqualTo(uuid.toString());
        }
    }
}
