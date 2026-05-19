package io.pravah.gateway.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit breaker configuration for gateway routes (ADR-012, LLD-01).
 *
 * <p>Implements the circuit breaker pattern to prevent cascading failures when backend services are
 * unavailable or slow.
 */
@Configuration
public class CircuitBreakerConfig {

  @Bean
  public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
    return factory ->
        factory.configureDefault(
            id ->
                new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(
                        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            .slidingWindowType(
                                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
                                    .SlidingWindowType.COUNT_BASED)
                            .slidingWindowSize(10)
                            .failureRateThreshold(50)
                            .waitDurationInOpenState(Duration.ofSeconds(10))
                            .permittedNumberOfCallsInHalfOpenState(3)
                            .slowCallRateThreshold(80)
                            .slowCallDurationThreshold(Duration.ofSeconds(5))
                            .build())
                    .timeLimiterConfig(
                        TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(30)).build())
                    .build());
  }

  @Bean
  public CircuitBreakerRegistry circuitBreakerRegistry() {
    return CircuitBreakerRegistry.of(
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build());
  }

  @Bean
  public TimeLimiterRegistry timeLimiterRegistry() {
    return TimeLimiterRegistry.of(
        TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(30)).build());
  }
}
