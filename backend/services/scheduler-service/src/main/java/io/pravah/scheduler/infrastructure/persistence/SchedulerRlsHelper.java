package io.pravah.scheduler.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/** Enables transaction-scoped RLS bypass for the schedule evaluation leader job. */
@Component
public class SchedulerRlsHelper {

  @PersistenceContext private EntityManager entityManager;

  public void enableEvaluation() {
    setConfig("pravah.scheduler_evaluation_enabled", "true");
  }

  public void disableEvaluation() {
    setConfig("pravah.scheduler_evaluation_enabled", "false");
  }

  public void enableTriggerDispatchRelay() {
    setConfig("pravah.trigger_dispatch_relay_enabled", "true");
  }

  public void disableTriggerDispatchRelay() {
    setConfig("pravah.trigger_dispatch_relay_enabled", "false");
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
