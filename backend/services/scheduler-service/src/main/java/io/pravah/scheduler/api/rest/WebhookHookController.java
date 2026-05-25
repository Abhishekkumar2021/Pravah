package io.pravah.scheduler.api.rest;

import io.pravah.scheduler.api.dto.WebhookInvokeResponse;
import io.pravah.scheduler.application.WebhookTriggerService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public webhook ingress (US-03.07). */
@RestController
@RequestMapping("/api/v1/hooks")
public class WebhookHookController {

  private final WebhookTriggerService webhookTriggerService;

  public WebhookHookController(WebhookTriggerService webhookTriggerService) {
    this.webhookTriggerService = webhookTriggerService;
  }

  @PostMapping("/{triggerId}")
  public WebhookInvokeResponse invoke(
      @PathVariable UUID triggerId,
      @RequestHeader(value = WebhookTriggerService.SECRET_HEADER, required = false) String secret,
      @RequestBody(required = false) Map<String, Object> body) {
    return webhookTriggerService.invoke(triggerId, secret, body);
  }
}
