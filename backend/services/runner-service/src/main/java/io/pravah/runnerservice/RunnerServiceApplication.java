package io.pravah.runnerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runner Service Application Entry Point.
 *
 * <p>Manages the runner fleet: registration, heartbeats, job assignment, and monitoring.
 *
 * <ul>
 *   <li>gRPC server for runner communication (bidirectional streaming)
 *   <li>REST API for fleet management
 *   <li>Heartbeat monitoring and stale runner detection
 *   <li>Job assignment to available runners
 * </ul>
 *
 * @see <a href="../../../docs/adr/ADR-005-grpc-runner-communication.md">ADR-005</a>
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {
      "io.pravah.runnerservice",
      "io.pravah.spring.security",
      "io.pravah.spring.multitenancy",
      "io.pravah.spring.vault"
    })
@EnableScheduling
public class RunnerServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(RunnerServiceApplication.class, args);
  }
}
