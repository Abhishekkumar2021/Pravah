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
 *   <li>Should only be called from AuthService or API token authentication
 * </ul>
 *
 * @see V4__auth_rls_bypass.sql
 * @see V5__api_token_rls_bypass.sql
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
    setConfig("pravah.auth_lookup_enabled", "true");
  }

  /**
   * Disables RLS bypass after auth lookup is complete.
   *
   * <p>Called after the user lookup to ensure subsequent operations in the same transaction (like
   * recording login attempts) use proper tenant context.
   */
  public void disableAuthLookup() {
    setConfig("pravah.auth_lookup_enabled", "false");
  }

  /** Enables RLS bypass for API token hash lookup within the current transaction. */
  public void enableApiTokenLookup() {
    setConfig("pravah.api_token_lookup_enabled", "true");
  }

  /** Disables API token RLS bypass after lookup completes. */
  public void disableApiTokenLookup() {
    setConfig("pravah.api_token_lookup_enabled", "false");
  }

  private void setConfig(String key, String value) {
    Session session = entityManager.unwrap(Session.class);
    session.doWork(
        connection -> {
          try (PreparedStatement ps =
              connection.prepareStatement("SELECT set_config(?, ?, true)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.execute();
          }
        });
  }
}
