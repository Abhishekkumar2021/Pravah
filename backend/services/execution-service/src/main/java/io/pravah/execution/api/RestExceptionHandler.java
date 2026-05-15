package io.pravah.execution.api;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.PravahException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

  @ExceptionHandler(EntityNotFoundException.class)
  public ProblemDetail notFound(EntityNotFoundException ex) {
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

  @ExceptionHandler(PravahException.class)
  public ProblemDetail pravahException(PravahException ex) {
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

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail badRequest(IllegalArgumentException ex) {
    log.debug("Bad request", kv("message", ex.getMessage()));
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setTitle("Bad Request");
    pd.setType(URI.create("about:blank"));
    return pd;
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ProblemDetail optimisticLock(ObjectOptimisticLockingFailureException ex) {
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
  public ProblemDetail validation(MethodArgumentNotValidException ex) {
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

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail server(IllegalStateException ex) {
    log.error("Internal server error", kv("message", ex.getMessage()), ex);
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    pd.setTitle("Internal Error");
    pd.setType(URI.create("about:blank"));
    return pd;
  }
}
