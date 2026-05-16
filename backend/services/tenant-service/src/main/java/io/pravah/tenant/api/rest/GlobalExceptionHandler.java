package io.pravah.tenant.api.rest;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.AccessDeniedException;
import io.pravah.common.exception.AuthenticationException;
import io.pravah.common.exception.ConcurrencyException;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.InvalidStateTransitionException;
import io.pravah.common.exception.PravahException;
import io.pravah.common.exception.QuotaExceededException;
import io.pravah.common.exception.ValidationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler that translates domain exceptions to RFC 7807 Problem Detail responses.
 *
 * <p>Ensures consistent error responses across all endpoints and prevents stack trace leakage.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(EntityNotFoundException.class)
  public ProblemDetail handleNotFound(EntityNotFoundException ex) {
    log.warn(
        "Entity not found", kv("errorCode", ex.getErrorCode()), kv("message", ex.getMessage()));

    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setTitle("Resource Not Found");
    pd.setType(URI.create("https://pravah.io/errors/not-found"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(ValidationException.class)
  public ProblemDetail handleValidation(ValidationException ex) {
    log.warn(
        "Validation failed",
        kv("errorCode", ex.getErrorCode()),
        kv("fieldErrors", ex.getFieldErrors()));

    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setTitle("Validation Error");
    pd.setType(URI.create("https://pravah.io/errors/validation"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("fieldErrors", ex.getFieldErrors());
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
    List<FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldError(e.getField(), e.getDefaultMessage(), e.getRejectedValue()))
            .toList();

    log.warn("Bean validation failed", kv("fieldErrors", errors));

    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    pd.setTitle("Validation Error");
    pd.setType(URI.create("https://pravah.io/errors/validation"));
    pd.setProperty("fieldErrors", errors);
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(AuthenticationException ex) {
    log.debug("Authentication failed", kv("message", ex.getMessage()));
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    pd.setTitle("Unauthorized");
    pd.setType(URI.create("https://pravah.io/errors/unauthorized"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    log.warn("Access denied", kv("errorCode", ex.getErrorCode()), kv("message", ex.getMessage()));

    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    pd.setTitle("Access Denied");
    pd.setType(URI.create("https://pravah.io/errors/forbidden"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(ConcurrencyException.class)
  public ProblemDetail handleConcurrency(ConcurrencyException ex) {
    log.warn(
        "Concurrency conflict", kv("errorCode", ex.getErrorCode()), kv("message", ex.getMessage()));

    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Concurrency Conflict");
    pd.setType(URI.create("https://pravah.io/errors/conflict"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(InvalidStateTransitionException.class)
  public ProblemDetail handleInvalidStateTransition(InvalidStateTransitionException ex) {
    log.warn(
        "Invalid state transition",
        kv("errorCode", ex.getErrorCode()),
        kv("message", ex.getMessage()));

    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Invalid State Transition");
    pd.setType(URI.create("https://pravah.io/errors/invalid-state"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(QuotaExceededException.class)
  public ProblemDetail handleQuotaExceeded(QuotaExceededException ex) {
    log.warn("Quota exceeded", kv("errorCode", ex.getErrorCode()), kv("message", ex.getMessage()));

    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    pd.setTitle("Quota Exceeded");
    pd.setType(URI.create("https://pravah.io/errors/quota-exceeded"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleIllegalState(IllegalStateException ex) {
    log.warn("Illegal state", kv("message", ex.getMessage()));

    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Operation Not Allowed");
    pd.setType(URI.create("https://pravah.io/errors/illegal-state"));
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    log.warn("Illegal argument", kv("message", ex.getMessage()));

    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setTitle("Invalid Request");
    pd.setType(URI.create("https://pravah.io/errors/bad-request"));
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(PravahException.class)
  public ProblemDetail handlePravahException(PravahException ex) {
    log.error(
        "Unhandled Pravah exception",
        kv("errorCode", ex.getErrorCode()),
        kv("message", ex.getMessage()),
        ex);

    HttpStatus status = HttpStatus.resolve(ex.suggestedHttpStatus());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    pd.setTitle("Application Error");
    pd.setType(URI.create("https://pravah.io/errors/application"));
    pd.setProperty("errorCode", ex.getErrorCode());
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unexpected error", kv("type", ex.getClass().getName()), ex);

    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    pd.setTitle("Internal Server Error");
    pd.setType(URI.create("https://pravah.io/errors/internal"));
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }

  record FieldError(String field, String message, Object rejectedValue) {}
}
