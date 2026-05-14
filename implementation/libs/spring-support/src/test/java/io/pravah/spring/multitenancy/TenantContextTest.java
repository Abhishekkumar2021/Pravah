package io.pravah.spring.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  @BeforeEach
  void setUp() {
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void setAndGetTenantId() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    assertThat(TenantContext.getCurrentTenantId()).isEqualTo(tenantId);
  }

  @Test
  void setAndGetUserId() {
    UUID userId = UUID.randomUUID();
    TenantContext.setCurrentUserId(userId);
    assertThat(TenantContext.getCurrentUserId()).isEqualTo(userId);
  }

  @Test
  void hasTenantContext_returnsTrueWhenSet() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    assertThat(TenantContext.hasTenantContext()).isTrue();
  }

  @Test
  void hasTenantContext_returnsFalseWhenNotSet() {
    assertThat(TenantContext.hasTenantContext()).isFalse();
  }

  @Test
  void hasUserContext_returnsTrueWhenSet() {
    TenantContext.setCurrentUserId(UUID.randomUUID());
    assertThat(TenantContext.hasUserContext()).isTrue();
  }

  @Test
  void hasUserContext_returnsFalseWhenNotSet() {
    assertThat(TenantContext.hasUserContext()).isFalse();
  }

  @Test
  void clear_removesBothContexts() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    TenantContext.setCurrentUserId(UUID.randomUUID());

    TenantContext.clear();

    assertThat(TenantContext.getCurrentTenantId()).isNull();
    assertThat(TenantContext.getCurrentUserId()).isNull();
  }

  @Test
  void defaultValues_areNull() {
    assertThat(TenantContext.getCurrentTenantId()).isNull();
    assertThat(TenantContext.getCurrentUserId()).isNull();
  }
}
