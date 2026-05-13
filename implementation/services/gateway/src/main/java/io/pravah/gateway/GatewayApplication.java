package io.pravah.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application Entry Point.
 * <p>
 * Serves as the single entry point for all external API requests.
 * Responsibilities:
 * <ul>
 *   <li>Authentication and authorization</li>
 *   <li>Rate limiting per tenant</li>
 *   <li>Request routing to backend services</li>
 *   <li>Request/response transformation</li>
 * </ul>
 *
 * @see <a href="../../../docs/architecture/high-level-architecture.md">High-Level Architecture</a>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
