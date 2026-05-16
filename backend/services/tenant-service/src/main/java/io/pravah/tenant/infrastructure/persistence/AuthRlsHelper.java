package io.pravah.tenant.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Helper for bypassing RLS during authentication operations.
 *
 * <p>Authentication requires looking up users by email before the tenant context is known (the
 * user's tenant comes from the user record itself). This helper sets a transaction-local flag that
 * enables a SELECT-only RLS policy for auth lookups.
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>Only enables SELECT access (no writes)
 *   <li>Flag is transaction-scoped (cleared on commit/rollback)
 *   <li>Should only be called from AuthService
 * </ul>
 *
 * @see V4__auth_rls_bypass.sql
 */
@Component
public class AuthRlsHelper {

  @PersistenceContext private EntityManager entityManager;

  /**
   * Enables RLS bypass for auth email lookups within the current transaction.
   *
   * <p>Must be called at the start of an auth operation that needs to query users by email. The
   * flag is automatically cleared when the transaction ends.
   */
  public void enableAuthLookup() {
    Session session = entityManager.unwrap(Session.class);
    session.doWork(
        connection -> {
          try (PreparedStatement ps =
              connection.prepareStatement(
                  "SELECT set_config('pravah.auth_lookup_enabled', 'true', true)")) {
            ps.execute();
          }
        });
  }

  /**
   * Disables RLS bypass after auth lookup is complete.
   *
   * <p>Called after the user lookup to ensure subsequent operations in the same transaction (like
   * recording login attempts) use proper tenant context.
   */
  public void disableAuthLookup() {
    Session session = entityManager.unwrap(Session.class);
    session.doWork(
        connection -> {
          try (PreparedStatement ps =
              connection.prepareStatement(
                  "SELECT set_config('pravah.auth_lookup_enabled', 'false', true)")) {
            ps.execute();
          }
        });
  }
}
