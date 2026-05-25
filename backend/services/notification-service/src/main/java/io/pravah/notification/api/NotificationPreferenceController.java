package io.pravah.notification.api;

import io.pravah.notification.api.dto.NotificationPreferenceResponse;
import io.pravah.notification.api.dto.UpdateNotificationPreferenceRequest;
import io.pravah.notification.application.NotificationPreferenceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-preferences")
public class NotificationPreferenceController {

  private final NotificationPreferenceService preferenceService;

  public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
    this.preferenceService = preferenceService;
  }

  @GetMapping
  @PreAuthorize("@permissionChecker.hasAny('settings:read', 'users:*')")
  public NotificationPreferenceResponse get() {
    return preferenceService.getPreferences();
  }

  @PutMapping
  @PreAuthorize("@permissionChecker.hasAny('users:*', 'settings:read')")
  public NotificationPreferenceResponse update(
      @RequestBody UpdateNotificationPreferenceRequest request) {
    return preferenceService.updatePreferences(request);
  }
}
