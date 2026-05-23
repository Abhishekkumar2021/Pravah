package io.pravah.common.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantArtifactKeysTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

  @Test
  void isOwnedByTenant_acceptsMatchingPrefix() {
    assertThat(
            TenantArtifactKeys.isOwnedByTenant(
                "tenants/" + TENANT_ID + "/executions/x/jobs/y/output/data.json", TENANT_ID))
        .isTrue();
  }

  @Test
  void requireOwnedByTenant_rejectsCrossTenantKey() {
    UUID other = UUID.fromString("22222222-2222-4222-8222-222222222222");
    assertThatThrownBy(
            () ->
                TenantArtifactKeys.requireOwnedByTenant(
                    "tenants/" + other + "/executions/x/jobs/y/output/data.json", TENANT_ID))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
