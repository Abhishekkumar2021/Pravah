package io.pravah.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification Service Application Entry Point.
 *
 * <p>Handles multi-channel notifications (email, Slack, webhooks). Consumes events from Kafka to
 * trigger notifications.
 *
 * @see <a href="../../../docs/lld/02-database-erd.md">Database ERD - Notification Domain</a>
 */
@SpringBootApplication
public class NotificationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
