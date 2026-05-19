package io.pravah.runnerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runner Service Application Entry Point.
 *
 * <p>Manages the runner fleet: registration, heartbeats, job assignment, and monitoring.
 *
 * <ul>
 *   <li>gRPC server for runner communication (bidirectional streaming)</li>
 *   <li>REST API for fleet management</li>
 *   <li>Heartbeat monitoring and stale runner detection</li>
 *   <li>Job assignment to available runners</li>
 * </ul>
 *
 * @see <a href="../../../docs/adr/ADR-005-grpc-runner-communication.md">ADR-005</a>
 */
@SpringBootApplication
@EnableScheduling
public class RunnerServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(RunnerServiceApplication.class, args);
  }
}
