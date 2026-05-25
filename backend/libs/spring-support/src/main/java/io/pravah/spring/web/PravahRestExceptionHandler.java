package io.pravah.spring.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.AccessDeniedException;
import io.pravah.common.exception.AuthenticationException;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.InvalidStateTransitionException;
import io.pravah.common.exception.PravahException;
import io.pravah.common.exception.ValidationException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Shared REST exception handler for all Pravah services.
 *
 * <p>Provides consistent ProblemDetail responses (RFC 9457) across services. Services can extend
 * this class to add service-specific exception handlers while inheriting the common ones.
 *
 * <p>Marked with lowest precedence so service-specific handlers run first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class PravahRestExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(PravahRestExceptionHandler.class);

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    log.debug(
        "Access denied",
        kv("resource", ex.getResource()),
        kv("action", ex.getAction()),
        kv("message", ex.getMessage()));
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(ex.suggestedHttpStatus()), ex.getMessage());
    pd.setTitle("Forbidden");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("resource", ex.getResource());
    pd.setProperty("action", ex.getAction());
    return pd;
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(AuthenticationException ex) {
    log.debug("Authentication failed", kv("message", ex.getMessage()));
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(ex.suggestedHttpStatus()), ex.getMessage());
    pd.setTitle("Unauthorized");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", ex.getErrorCode());
    return pd;
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ProblemDetail handleNotFound(EntityNotFoundException ex) {
    log.debug(
        "Entity not found",
        kv("entity_type", ex.getEntityType()),
        kv("entity_id", ex.getEntityId()));
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(ex.suggestedHttpStatus()), ex.getMessage());
    pd.setTitle("Not Found");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("entityType", ex.getEntityType());
    pd.setProperty("entityId", ex.getEntityId());
    return pd;
  }

  @ExceptionHandler(InvalidStateTransitionException.class)
  public ProblemDetail handleInvalidTransition(InvalidStateTransitionException ex) {
    log.debug("Invalid state transition", kv("message", ex.getMessage()));
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(ex.suggestedHttpStatus()), ex.getMessage());
    pd.setTitle("Invalid State Transition");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("entityType", ex.getEntityType());
    pd.setProperty("entityId", ex.getEntityId());
    pd.setProperty("currentState", ex.getCurrentState());
    pd.setProperty("attemptedTransition", ex.getAttemptedTransition());
    return pd;
  }

  @ExceptionHandler(ValidationException.class)
  public ProblemDetail handleValidation(ValidationException ex) {
    log.debug(
        "Validation error", kv("errors", ex.getFieldErrors()), kv("message", ex.getMessage()));
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(ex.suggestedHttpStatus()), ex.getMessage());
    pd.setTitle("Validation Error");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", ex.getErrorCode());
    if (!ex.getFieldErrors().isEmpty()) {
      pd.setProperty("fieldErrors", ex.getFieldErrors());
    }
    return pd;
  }

  @ExceptionHandler(PravahException.class)
  public ProblemDetail handlePravahException(PravahException ex) {
    log.debug(
        "Pravah exception", kv("error_code", ex.getErrorCode()), kv("message", ex.getMessage()));
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(ex.suggestedHttpStatus()), ex.getMessage());
    pd.setTitle("Error");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", ex.getErrorCode());
    return pd;
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
    log.warn("Optimistic locking conflict", kv("message", ex.getMessage()));
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "Resource was modified concurrently; retry the request");
    pd.setTitle("Conflict");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", "OPTIMISTIC_LOCK_CONFLICT");
    return pd;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
    log.debug("Validation error", kv("message", msg));
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
    pd.setTitle("Validation Error");
    pd.setType(URI.create("about:blank"));
    return pd;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
    log.debug("Bad request", kv("message", ex.getMessage()));
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, sanitizeClientMessage(ex));
    pd.setTitle("Bad Request");
    pd.setType(URI.create("about:blank"));
    return pd;
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleInternalError(IllegalStateException ex) {
    log.error("Internal server error", kv("message", ex.getMessage()), ex);
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred");
    pd.setTitle("Internal Error");
    pd.setType(URI.create("about:blank"));
    return pd;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", kv("message", ex.getMessage()), ex);
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred");
    pd.setTitle("Internal Error");
    pd.setType(URI.create("about:blank"));
    return pd;
  }

  private static String sanitizeClientMessage(IllegalArgumentException ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return "Invalid request";
    }
    return message;
  }
}
