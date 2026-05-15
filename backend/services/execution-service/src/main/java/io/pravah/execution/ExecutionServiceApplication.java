package io.pravah.execution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Execution Service Application Entry Point.
 *
 * <p>Orchestrates pipeline runs and manages job lifecycle. Implements Run and Job state machines.
 *
 * @see <a href="../../../docs/lld/03-state-machines.md">State Machines - Run and Job States</a>
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(
    basePackages = {
      "io.pravah.execution",
      "io.pravah.spring.multitenancy",
      "io.pravah.spring.security"
    })
public class ExecutionServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ExecutionServiceApplication.class, args);
  }
}
