package io.pravah.execution.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TriggerExecutionIdempotencyId implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID tenantId;
  private String idempotencyKey;

  public TriggerExecutionIdempotencyId() {}

  public TriggerExecutionIdempotencyId(UUID tenantId, String idempotencyKey) {
    this.tenantId = tenantId;
    this.idempotencyKey = idempotencyKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TriggerExecutionIdempotencyId that)) {
      return false;
    }
    return Objects.equals(tenantId, that.tenantId)
        && Objects.equals(idempotencyKey, that.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, idempotencyKey);
  }
}
