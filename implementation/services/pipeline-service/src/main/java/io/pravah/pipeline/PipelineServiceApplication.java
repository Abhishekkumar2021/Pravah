package io.pravah.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Pipeline Service Application Entry Point.
 * <p>
 * Manages pipeline definitions, versions, and YAML validation.
 * Implements the Pipeline state machine for lifecycle management.
 *
 * @see <a href="../../../docs/lld/03-state-machines.md">State Machines - Pipeline States</a>
 */
@SpringBootApplication
public class PipelineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PipelineServiceApplication.class, args);
    }
}
