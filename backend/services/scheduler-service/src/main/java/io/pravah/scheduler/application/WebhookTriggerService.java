package io.pravah.scheduler.application;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.scheduler.api.dto.WebhookInvokeResponse;
import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.domain.model.TriggerType;
import io.pravah.scheduler.domain.repository.PipelineTriggerRepository;
import io.pravah.scheduler.infrastructure.persistence.TriggerRlsHelper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebhookTriggerService {

  public static final String SECRET_HEADER = "X-Pravah-Webhook-Secret";

  private final PipelineTriggerRepository triggerRepository;
  private final TriggerRlsHelper triggerRlsHelper;
  private final PasswordEncoder passwordEncoder;
  private final WebhookRateLimiter rateLimiter;
  private final PipelineTriggerDispatchService dispatchService;

  public WebhookTriggerService(
      PipelineTriggerRepository triggerRepository,
      TriggerRlsHelper triggerRlsHelper,
      PasswordEncoder passwordEncoder,
      WebhookRateLimiter rateLimiter,
      PipelineTriggerDispatchService dispatchService) {
    this.triggerRepository = triggerRepository;
    this.triggerRlsHelper = triggerRlsHelper;
    this.passwordEncoder = passwordEncoder;
    this.rateLimiter = rateLimiter;
    this.dispatchService = dispatchService;
  }

  @Transactional
  public WebhookInvokeResponse invoke(UUID triggerId, String secret, Map<String, Object> body) {
    PipelineTrigger trigger = loadWebhookTrigger(triggerId);
    validateSecret(trigger, secret);

    Map<String, Object> config = TriggerConfigSupport.parseConfig(trigger.getConfig());
    int limit = TriggerConfigSupport.rateLimitPerMinute(config);
    if (!rateLimiter.tryAcquire(triggerId, limit)) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Webhook rate limit exceeded");
    }

    UUID executionId = dispatchService.dispatch(trigger, body);
    return new WebhookInvokeResponse(executionId);
  }

  private PipelineTrigger loadWebhookTrigger(UUID triggerId) {
    triggerRlsHelper.enableWebhookLookup();
    try {
      PipelineTrigger trigger =
          triggerRepository
              .findById(triggerId)
              .orElseThrow(() -> new EntityNotFoundException("PipelineTrigger", triggerId));
      if (trigger.getTriggerType() != TriggerType.WEBHOOK) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook trigger not found");
      }
      if (!trigger.isEnabled()) {
        throw new ResponseStatusException(HttpStatus.GONE, "Webhook trigger is disabled");
      }
      return trigger;
    } finally {
      triggerRlsHelper.disableWebhookLookup();
    }
  }

  private void validateSecret(PipelineTrigger trigger, String secret) {
    if (secret == null || secret.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing webhook secret header");
    }
    if (trigger.getSecretHash() == null
        || !passwordEncoder.matches(secret, trigger.getSecretHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook secret");
    }
  }
}
