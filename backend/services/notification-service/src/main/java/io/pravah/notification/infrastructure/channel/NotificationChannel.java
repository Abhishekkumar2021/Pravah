package io.pravah.notification.infrastructure.channel;

import io.pravah.notification.application.dto.AlertContext;

/** Interface for notification delivery channels. */
public interface NotificationChannel {

  /** Channel type identifier (email, slack, webhook). */
  String type();

  /**
   * Send a notification through this channel.
   *
   * @param context Alert context with event details
   * @param channelConfig Channel-specific configuration (JSON parsed to Map)
   * @return Delivery result
   */
  DeliveryResult send(AlertContext context, java.util.Map<String, Object> channelConfig);
}
