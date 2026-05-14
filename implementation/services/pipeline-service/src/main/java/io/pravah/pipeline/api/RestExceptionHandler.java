package io.pravah.pipeline.api;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.InvalidStateTransitionException;
import io.pravah.common.exception.PravahException;
import io.pravah.pipeline.application.DuplicatePipelineNameException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

  @ExceptionHandler(EntityNotFoundException.class)
  public ProblemDetail notFound(EntityNotFoundException ex) {
    log.debug("Entity not found: {} with ID {}", ex.getEntityType(), ex.getEntityId());
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
  public ProblemDetail invalidTransition(InvalidStateTransitionException ex) {
    log.debug("Invalid state transition: {}", ex.getMessage());
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

  @ExceptionHandler(PravahException.class)
  public ProblemDetail pravahException(PravahException ex) {
    log.debug("Pravah exception: {} - {}", ex.getErrorCode(), ex.getMessage());
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(ex.suggestedHttpStatus()), ex.getMessage());
    pd.setTitle("Error");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", ex.getErrorCode());
    return pd;
  }

  @ExceptionHandler(DuplicatePipelineNameException.class)
  public ProblemDetail conflict(DuplicatePipelineNameException ex) {
    log.debug("Conflict: {}", ex.getMessage());
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Conflict");
    pd.setType(URI.create("about:blank"));
    return pd;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail badRequest(IllegalArgumentException ex) {
    log.debug("Bad request: {}", ex.getMessage());
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setTitle("Bad Request");
    pd.setType(URI.create("about:blank"));
    return pd;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail validation(MethodArgumentNotValidException ex) {
    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
    log.debug("Validation error: {}", msg);
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
    pd.setTitle("Validation Error");
    pd.setType(URI.create("about:blank"));
    return pd;
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail server(IllegalStateException ex) {
    log.error("Internal server error", ex);
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    pd.setTitle("Internal Error");
    pd.setType(URI.create("about:blank"));
    return pd;
  }
}
