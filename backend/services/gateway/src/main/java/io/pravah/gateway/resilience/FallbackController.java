package io.pravah.gateway.resilience;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Fallback controller for circuit breaker responses.
 *
 * <p>Returns a consistent error response when backend services are unavailable due to circuit
 * breaker activation.
 */
@RestController
public class FallbackController {

  @GetMapping("/fallback")
  public Mono<ResponseEntity<FallbackResponse>> fallbackGet() {
    return fallbackResponse();
  }

  @PostMapping("/fallback")
  public Mono<ResponseEntity<FallbackResponse>> fallbackPost() {
    return fallbackResponse();
  }

  private Mono<ResponseEntity<FallbackResponse>> fallbackResponse() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                new FallbackResponse(
                    "SERVICE_UNAVAILABLE",
                    "The service is temporarily unavailable. Please try again later.",
                    Instant.now().toString())));
  }

  public record FallbackResponse(String code, String message, String timestamp) {}
}
