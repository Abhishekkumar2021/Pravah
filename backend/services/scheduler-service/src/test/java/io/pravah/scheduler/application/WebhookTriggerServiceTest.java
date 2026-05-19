package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.domain.model.TriggerType;
import io.pravah.scheduler.domain.repository.PipelineTriggerRepository;
import io.pravah.scheduler.infrastructure.persistence.TriggerRlsHelper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WebhookTriggerServiceTest {

  @Mock private PipelineTriggerRepository triggerRepository;
  @Mock private TriggerRlsHelper triggerRlsHelper;
  @Mock private WebhookRateLimiter rateLimiter;
  @Mock private PipelineTriggerDispatchService dispatchService;

  private WebhookTriggerService service;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

  @BeforeEach
  void setUp() {
    service =
        new WebhookTriggerService(
            triggerRepository, triggerRlsHelper, encoder, rateLimiter, dispatchService);
  }

  @Test
  void invoke_rejectsInvalidSecret() {
    UUID triggerId = UUID.randomUUID();
    String secret = "whsec_test";
    PipelineTrigger trigger =
        PipelineTrigger.builder()
            .tenantId(UUID.randomUUID())
            .pipelineId(UUID.randomUUID())
            .name("hook")
            .triggerType(TriggerType.WEBHOOK)
            .config("{}")
            .secretHash(encoder.encode(secret))
            .createdBy(UUID.randomUUID())
            .build();

    when(triggerRepository.findById(triggerId)).thenReturn(Optional.of(trigger));

    assertThatThrownBy(() -> service.invoke(triggerId, "wrong", Map.of()))
        .isInstanceOf(ResponseStatusException.class);

    verify(dispatchService, never()).dispatch(any(), any());
  }
}
