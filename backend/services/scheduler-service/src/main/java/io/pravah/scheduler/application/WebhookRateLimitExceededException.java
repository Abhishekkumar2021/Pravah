package io.pravah.scheduler.application;

import java.io.Serial;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;

/** HTTP 429 when a webhook trigger exceeds its Redis rate limit (US-03.07, US-10.14). */
public class WebhookRateLimitExceededException extends ResponseStatusException
    implements ErrorResponse {

  @Serial private static final long serialVersionUID = 1L;

  private final int retryAfterSeconds;

  public WebhookRateLimitExceededException(int retryAfterSeconds) {
    super(HttpStatus.TOO_MANY_REQUESTS, "Webhook rate limit exceeded");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  @Override
  public HttpHeaders getHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    return headers;
  }
}
