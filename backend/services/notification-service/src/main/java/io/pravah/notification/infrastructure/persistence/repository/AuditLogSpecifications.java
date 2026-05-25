package io.pravah.notification.infrastructure.persistence.repository;

import io.pravah.notification.infrastructure.persistence.entity.AuditLogEntity;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic JPA criteria for audit log queries (avoids untyped NULL bind params in JPQL). */
public final class AuditLogSpecifications {

  private AuditLogSpecifications() {}

  public static Specification<AuditLogEntity> filtered(
      UUID tenantId,
      String action,
      String resourceType,
      UUID resourceId,
      UUID actorId,
      Instant from,
      Instant to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));
      if (action != null && !action.isBlank()) {
        predicates.add(cb.equal(root.get("action"), action));
      }
      if (resourceType != null && !resourceType.isBlank()) {
        predicates.add(cb.equal(root.get("resourceType"), resourceType));
      }
      if (resourceId != null) {
        predicates.add(cb.equal(root.get("resourceId"), resourceId));
      }
      if (actorId != null) {
        predicates.add(cb.equal(root.get("actorId"), actorId));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
