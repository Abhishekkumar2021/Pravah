package io.pravah.notification.api;

import io.pravah.notification.api.dto.UserNotificationResponse;
import io.pravah.notification.application.UserNotificationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class UserNotificationController {

  private final UserNotificationService notificationService;

  public UserNotificationController(UserNotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public Page<UserNotificationResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return notificationService.getNotifications(page, size);
  }

  @GetMapping("/unread")
  public List<UserNotificationResponse> getUnread() {
    return notificationService.getUnreadNotifications();
  }

  @GetMapping("/unread/count")
  public Map<String, Long> getUnreadCount() {
    return Map.of("count", notificationService.getUnreadCount());
  }

  @PostMapping("/{id}/read")
  public void markAsRead(@PathVariable UUID id) {
    notificationService.markAsRead(id);
  }

  @PostMapping("/read-all")
  public Map<String, Integer> markAllAsRead() {
    int count = notificationService.markAllAsRead();
    return Map.of("marked", count);
  }
}
