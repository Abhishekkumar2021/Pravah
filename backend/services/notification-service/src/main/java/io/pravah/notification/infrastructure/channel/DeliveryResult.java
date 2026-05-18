package io.pravah.notification.infrastructure.channel;

import java.time.Instant;

/** Result of a notification delivery attempt. */
public record DeliveryResult(
    String channel, boolean success, String message, Instant sentAt, String error) {

  public static DeliveryResult success(String channel) {
    return new DeliveryResult(channel, true, "Delivered", Instant.now(), null);
  }

  public static DeliveryResult success(String channel, String message) {
    return new DeliveryResult(channel, true, message, Instant.now(), null);
  }

  public static DeliveryResult failure(String channel, String error) {
    return new DeliveryResult(channel, false, "Failed", Instant.now(), error);
  }
}
