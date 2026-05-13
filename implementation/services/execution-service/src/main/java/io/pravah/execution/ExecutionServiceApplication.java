package io.pravah.execution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Execution Service Application Entry Point.
 * <p>
 * Orchestrates pipeline runs and manages job lifecycle.
 * Implements Run and Job state machines.
 *
 * @see <a href="../../../docs/lld/03-state-machines.md">State Machines - Run and Job States</a>
 */
@SpringBootApplication
public class ExecutionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutionServiceApplication.class, args);
    }
}
