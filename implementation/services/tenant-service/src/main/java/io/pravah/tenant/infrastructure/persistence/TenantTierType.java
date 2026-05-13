package io.pravah.tenant.infrastructure.persistence;

import io.pravah.tenant.domain.model.Tenant;

/** Hibernate UserType for mapping {@link Tenant.Tier} to PostgreSQL's tenant_tier ENUM type. */
public class TenantTierType extends PostgresEnumType<Tenant.Tier> {

  public TenantTierType() {
    super(Tenant.Tier.class);
  }
}
