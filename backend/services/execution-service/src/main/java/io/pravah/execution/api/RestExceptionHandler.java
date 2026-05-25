package io.pravah.execution.api;

import io.pravah.spring.web.PravahRestExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Execution-service exception handler. Inherits all common handlers from {@link
 * PravahRestExceptionHandler}; no service-specific exceptions to add currently.
 */
@RestControllerAdvice
public class RestExceptionHandler extends PravahRestExceptionHandler {}
