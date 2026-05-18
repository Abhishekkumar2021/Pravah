package io.pravah.notification.api.dto;

public record UpdateNotificationPreferenceRequest(
    Boolean emailEnabled,
    Boolean inAppEnabled,
    String eventPreferences,
    String quietHoursStart,
    String quietHoursEnd,
    String quietHoursTz) {}
