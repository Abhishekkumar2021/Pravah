package io.pravah.scheduler.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/** RLS bypass flags for webhook ingress and Kafka trigger consumers. */
@Component
public class TriggerRlsHelper {

  @PersistenceContext private EntityManager entityManager;

  public void enableWebhookLookup() {
    setConfig("pravah.webhook_lookup", "true");
  }

  public void disableWebhookLookup() {
    setConfig("pravah.webhook_lookup", "false");
  }

  public void enableKafkaConsumer() {
    setConfig("pravah.kafka_trigger_consumer", "true");
  }

  public void disableKafkaConsumer() {
    setConfig("pravah.kafka_trigger_consumer", "false");
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
