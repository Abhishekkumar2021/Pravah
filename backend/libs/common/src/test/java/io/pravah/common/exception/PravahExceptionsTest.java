package io.pravah.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PravahExceptionsTest {

  @Test
  void entityNotFoundException_exposesMetadata() {
    UUID id = UUID.randomUUID();
    var ex = new EntityNotFoundException("Pipeline", id);

    assertThat(ex.getEntityType()).isEqualTo("Pipeline");
    assertThat(ex.getEntityId()).isEqualTo(id.toString());
    assertThat(ex.suggestedHttpStatus()).isEqualTo(404);
  }

  @Test
  void validationException_supportsFieldErrors() {
    var ex = ValidationException.of("email", "Invalid");

    assertThat(ex.getFieldErrors()).hasSize(1);
    assertThat(ex.suggestedHttpStatus()).isEqualTo(400);
  }

  @Test
  void validationException_supportsRejectedValueAndMessageOnly() {
    var withValue = ValidationException.of("email", "Invalid", "bad@");
    assertThat(withValue.getFieldErrors().getFirst().rejectedValue()).isEqualTo("bad@");

    var messageOnly = new ValidationException("failed");
    assertThat(messageOnly.getFieldErrors()).isEmpty();
    assertThat(messageOnly.getMessage()).contains("failed");
  }

  @Test
  void entityNotFoundException_stringEntityId() {
    var ex = new EntityNotFoundException("Role", "owner");
    assertThat(ex.getEntityId()).isEqualTo("owner");
  }

  @Test
  void quotaExceededException_exposesQuotaDetails() {
    var ex = new QuotaExceededException("runs", 11, 10);
    assertThat(ex.getMessage()).contains("runs");
    assertThat(ex.suggestedHttpStatus()).isEqualTo(429);
  }

  @Test
  void accessDeniedException_has403Status() {
    assertThat(new AccessDeniedException("pipelines", "write").suggestedHttpStatus()).isEqualTo(403);
  }

  @Test
  void concurrencyException_has409Status() {
    assertThat(new ConcurrencyException("User", "id").suggestedHttpStatus()).isEqualTo(409);
  }

  @Test
  void quotaExceededException_has429Status() {
    assertThat(new QuotaExceededException("runs", 5, 5).suggestedHttpStatus()).isEqualTo(429);
  }

  @Test
  void invalidStateTransitionException_exposesTransition() {
    UUID id = UUID.randomUUID();
    var ex = new InvalidStateTransitionException("Execution", id.toString(), "pending", "running");

    assertThat(ex.getCurrentState()).isEqualTo("pending");
    assertThat(ex.getAttemptedTransition()).isEqualTo("running");
    assertThat(ex.suggestedHttpStatus()).isEqualTo(409);
  }
}
