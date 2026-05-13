package io.pravah.tenant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Tenant")
class TenantTest {

  @Nested
  @DisplayName("creation")
  class Creation {

    @Test
    @DisplayName("should create tenant with all required fields")
    void shouldCreateWithRequiredFields() {
      Tenant tenant = Tenant.builder().name("Acme Corp").slug("acme-corp").build();

      assertThat(tenant.getId()).isNotNull();
      assertThat(tenant.getName()).isEqualTo("Acme Corp");
      assertThat(tenant.getSlug()).isEqualTo("acme-corp");
      assertThat(tenant.getTier()).isEqualTo(Tenant.Tier.FREE);
      assertThat(tenant.getSettings()).isEqualTo("{}");
      assertThat(tenant.getCreatedAt()).isNotNull();
      assertThat(tenant.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should create tenant with custom tier")
    void shouldCreateWithCustomTier() {
      Tenant tenant =
          Tenant.builder()
              .name("Enterprise Corp")
              .slug("enterprise-corp")
              .tier(Tenant.Tier.ENTERPRISE)
              .build();

      assertThat(tenant.getTier()).isEqualTo(Tenant.Tier.ENTERPRISE);
    }

    @Test
    @DisplayName("should create tenant with specific ID")
    void shouldCreateWithSpecificId() {
      UUID id = UUID.randomUUID();
      Tenant tenant = Tenant.builder().id(id).name("Test Corp").slug("test-corp").build();

      assertThat(tenant.getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("should throw when name is null")
    void shouldThrowWhenNameIsNull() {
      assertThatThrownBy(() -> Tenant.builder().slug("test").build())
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw when slug is null")
    void shouldThrowWhenSlugIsNull() {
      assertThatThrownBy(() -> Tenant.builder().name("Test").build())
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("updates")
  class Updates {

    @Test
    @DisplayName("should update name")
    void shouldUpdateName() {
      Tenant tenant = Tenant.builder().name("Old Name").slug("test").build();

      tenant.updateName("New Name");

      assertThat(tenant.getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("should update tier")
    void shouldUpdateTier() {
      Tenant tenant = Tenant.builder().name("Test").slug("test").build();

      tenant.updateTier(Tenant.Tier.TEAM);

      assertThat(tenant.getTier()).isEqualTo(Tenant.Tier.TEAM);
    }

    @Test
    @DisplayName("should update settings")
    void shouldUpdateSettings() {
      Tenant tenant = Tenant.builder().name("Test").slug("test").build();

      tenant.updateSettings("{\"feature\":true}");

      assertThat(tenant.getSettings()).isEqualTo("{\"feature\":true}");
    }

    @Test
    @DisplayName("should default null settings to empty object")
    void shouldDefaultNullSettingsToEmptyObject() {
      Tenant tenant = Tenant.builder().name("Test").slug("test").settings("{\"old\":true}").build();

      tenant.updateSettings(null);

      assertThat(tenant.getSettings()).isEqualTo("{}");
    }
  }

  @Nested
  @DisplayName("equality")
  class Equality {

    @Test
    @DisplayName("should be equal when IDs match")
    void shouldBeEqualWhenIdsMatch() {
      UUID id = UUID.randomUUID();
      Tenant tenant1 = Tenant.builder().id(id).name("Test 1").slug("test-1").build();
      Tenant tenant2 = Tenant.builder().id(id).name("Test 2").slug("test-2").build();

      assertThat(tenant1).isEqualTo(tenant2);
      assertThat(tenant1.hashCode()).isEqualTo(tenant2.hashCode());
    }

    @Test
    @DisplayName("should not be equal when IDs differ")
    void shouldNotBeEqualWhenIdsDiffer() {
      Tenant tenant1 = Tenant.builder().name("Test").slug("test-1").build();
      Tenant tenant2 = Tenant.builder().name("Test").slug("test-1").build();

      assertThat(tenant1).isNotEqualTo(tenant2);
    }
  }
}
