package io.pravah.scheduler.infrastructure.leader;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Postgres-backed leader election for schedule evaluation (LLD §5). */
@Repository
public class SchedulerLeaderRepository {

  public static final String LOCK_NAME = "schedule_evaluator";

  @PersistenceContext private EntityManager entityManager;

  /**
   * Attempts to acquire or renew leadership.
   *
   * @return true if this holder is the current leader
   */
  @SuppressWarnings("unchecked")
  public boolean tryAcquireOrRenew(String holderId, Instant expiresAt) {
    List<?> results =
        entityManager
            .createNativeQuery(
                """
                INSERT INTO scheduler_locks (lock_name, holder_id, acquired_at, expires_at)
                VALUES (:lockName, :holderId, now(), :expiresAt)
                ON CONFLICT (lock_name) DO UPDATE
                SET holder_id = EXCLUDED.holder_id,
                    acquired_at = EXCLUDED.acquired_at,
                    expires_at = EXCLUDED.expires_at
                WHERE scheduler_locks.expires_at < now()
                   OR scheduler_locks.holder_id = EXCLUDED.holder_id
                RETURNING holder_id
                """)
            .setParameter("lockName", LOCK_NAME)
            .setParameter("holderId", holderId)
            .setParameter("expiresAt", expiresAt)
            .getResultList();
    if (results.isEmpty()) {
      return false;
    }
    return holderId.equals(results.get(0).toString());
  }
}
