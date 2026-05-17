package io.pravah.pipeline.api;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.pipeline.application.DuplicateConnectionNameException;
import io.pravah.pipeline.application.DuplicatePipelineNameException;
import io.pravah.pipeline.application.DuplicateSecretNameException;
import io.pravah.spring.web.PravahRestExceptionHandler;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Pipeline-service specific exception handlers. Inherits common handlers from {@link
 * PravahRestExceptionHandler}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestExceptionHandler extends PravahRestExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

  @ExceptionHandler(DuplicateConnectionNameException.class)
  public ProblemDetail handleDuplicateConnectionName(DuplicateConnectionNameException ex) {
    log.debug("Conflict", kv("message", ex.getMessage()));
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Conflict");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", "DUPLICATE_CONNECTION_NAME");
    return pd;
  }

  @ExceptionHandler(DuplicatePipelineNameException.class)
  public ProblemDetail handleDuplicatePipelineName(DuplicatePipelineNameException ex) {
    log.debug("Conflict", kv("message", ex.getMessage()));
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Conflict");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", "DUPLICATE_PIPELINE_NAME");
    return pd;
  }

  @ExceptionHandler(DuplicateSecretNameException.class)
  public ProblemDetail handleDuplicateSecretName(DuplicateSecretNameException ex) {
    log.debug("Conflict", kv("message", ex.getMessage()));
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setTitle("Conflict");
    pd.setType(URI.create("about:blank"));
    pd.setProperty("errorCode", "DUPLICATE_SECRET_NAME");
    return pd;
  }
}
