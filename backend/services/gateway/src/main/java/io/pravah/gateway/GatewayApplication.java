package io.pravah.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application Entry Point.
 *
 * <p>Serves as the single entry point for all external API requests. Responsibilities:
 *
 * <ul>
 *   <li>Authentication and authorization
 *   <li>Rate limiting per tenant
 *   <li>Request routing to backend services
 *   <li>Request/response transformation
 * </ul>
 *
 * @see <a href="../../../docs/architecture/high-level-architecture.md">High-Level Architecture</a>
 */
@SpringBootApplication(
    excludeName = "org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration")
public class GatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
