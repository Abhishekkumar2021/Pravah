package io.pravah.runnerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runner Service Application Entry Point.
 *
 * <p>Manages runner registration, heartbeats, and job assignment. Implements the gRPC bidirectional
 * streaming for runner communication.
 *
 * @see <a href="../../../docs/lld/03-state-machines.md">State Machines - Runner States</a>
 * @see <a href="../../../docs/architecture/api-contracts.md">API Contracts - gRPC</a>
 */
@SpringBootApplication
public class RunnerServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(RunnerServiceApplication.class, args);
  }
}
