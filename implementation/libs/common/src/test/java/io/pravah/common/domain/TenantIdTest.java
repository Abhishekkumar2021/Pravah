package io.pravah.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenantId")
class TenantIdTest {

    @Nested
    @DisplayName("Valid tenant IDs")
    class ValidTenantIds {

        @ParameterizedTest
        @ValueSource(strings = {
            "abc",
            "acme-corp",
            "tenant-123",
            "a-very-long-tenant-id-that-is-within-limits-abcdef",
            "a12"
        })
        @DisplayName("should accept valid tenant IDs")
        void shouldAcceptValidTenantIds(String value) {
            TenantId tenantId = TenantId.of(value);
            
            assertThat(tenantId.value()).isEqualTo(value);
            assertThat(tenantId.toString()).isEqualTo(value);
        }
    }

    @Nested
    @DisplayName("Invalid tenant IDs")
    class InvalidTenantIds {

        @Test
        @DisplayName("should reject null value")
        void shouldRejectNullValue() {
            assertThatThrownBy(() -> TenantId.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("null");
        }

        @Test
        @DisplayName("should reject blank value")
        void shouldRejectBlankValue() {
            assertThatThrownBy(() -> TenantId.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "ab",                    // Too short (< 3 chars)
            "1abc",                  // Starts with number
            "ABC",                   // Uppercase
            "tenant_id",             // Underscore not allowed
            "tenant.id",             // Dot not allowed
            "tenant id",             // Space not allowed
            "-tenant",               // Starts with hyphen
            "tenant-"                // Ends with hyphen
        })
        @DisplayName("should reject invalid formats")
        void shouldRejectInvalidFormats(String value) {
            assertThatThrownBy(() -> TenantId.of(value))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject tenant ID that is too long")
        void shouldRejectTooLongTenantId() {
            String tooLong = "a" + "b".repeat(63); // > 63 chars
            assertThatThrownBy(() -> TenantId.of(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when values match")
        void shouldBeEqualWhenValuesMatch() {
            TenantId id1 = TenantId.of("acme-corp");
            TenantId id2 = TenantId.of("acme-corp");

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when values differ")
        void shouldNotBeEqualWhenValuesDiffer() {
            TenantId id1 = TenantId.of("acme-corp");
            TenantId id2 = TenantId.of("other-corp");

            assertThat(id1).isNotEqualTo(id2);
        }
    }
}
