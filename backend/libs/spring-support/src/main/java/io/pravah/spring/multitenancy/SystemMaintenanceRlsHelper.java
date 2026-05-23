package io.pravah.spring.multitenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Enables transaction-scoped RLS bypass for scheduled system jobs (stale runner detection, job
 * timeouts, artifact cleanup). Services add matching {@code system_maintenance} RLS policies in
 * Flyway migrations.
 */
@Component
public class SystemMaintenanceRlsHelper {

  private static final String FLAG = "pravah.system_maintenance_enabled";

  @PersistenceContext private EntityManager entityManager;

  public void enable() {
    setConfig(FLAG, "true");
  }

  public void disable() {
    setConfig(FLAG, "false");
  }

  public <T> T runWithMaintenance(java.util.function.Supplier<T> action) {
    enable();
    try {
      return action.get();
    } finally {
      disable();
    }
  }

  public void runWithMaintenance(Runnable action) {
    enable();
    try {
      action.run();
    } finally {
      disable();
    }
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
