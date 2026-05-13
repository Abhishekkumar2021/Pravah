package io.pravah.playground.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service B: Downstream service called by Service A.
 * 
 * When Service A calls Service B:
 * 1. The traceparent header arrives with the request
 * 2. OpenTelemetry auto-extracts it and creates a child span
 * 3. The span has the same trace_id as Service A's span
 * 4. In Jaeger, both spans appear in the same trace
 */
@RestController
public class ServiceBController {

    private static final Logger log = LoggerFactory.getLogger(ServiceBController.class);

    private final Tracer tracer;

    public ServiceBController(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Simple processing endpoint.
     * Auto-instrumentation handles:
     * - Extracting trace context from incoming request
     * - Creating a server span as child of the caller's span
     */
    @GetMapping("/process")
    public String process() {
        log.info("Processing request in Service B");

        // Simulate some work
        simulateWork(100);

        return "Processed by Service B";
    }

    /**
     * Validation endpoint with manual span for database simulation.
     */
    @GetMapping("/validate/{id}")
    public String validate(@PathVariable String id) {
        log.info("Validating id={}", id);

        // Create a manual span to simulate a database call
        Span dbSpan = tracer.spanBuilder("db.query")
                .setAttribute("db.system", "postgresql")
                .setAttribute("db.operation", "SELECT")
                .setAttribute("db.statement", "SELECT * FROM validations WHERE id = ?")
                .startSpan();

        try (Scope scope = dbSpan.makeCurrent()) {
            // Simulate database latency
            simulateWork(30);

            dbSpan.setAttribute("db.rows_affected", 1);
        } finally {
            dbSpan.end();
        }

        // Determine validation result based on id
        boolean isValid = id.hashCode() % 2 == 0;

        Span.current().setAttribute("validation.result", isValid);

        return isValid ? "VALID" : "INVALID";
    }

    /**
     * Endpoint that simulates a slow operation.
     * Useful for testing tail-based sampling rules.
     */
    @GetMapping("/slow")
    public String slow() {
        log.info("Starting slow operation...");

        Span span = tracer.spanBuilder("slow-operation")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Simulate 3 seconds of work (triggers 100% sampling per ADR-014)
            simulateWork(3000);
            span.setAttribute("operation.duration_ms", 3000);
        } finally {
            span.end();
        }

        return "Completed slow operation";
    }

    private void simulateWork(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
