package io.pravah.playground.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Service A: Entry point that calls Service B.
 * 
 * Demonstrates:
 * 1. Auto-instrumented HTTP spans (Spring Boot starter handles this)
 * 2. Manual span creation for business logic
 * 3. Trace context propagation to downstream service
 * 4. Adding custom attributes to spans
 */
@RestController
public class ServiceAController {

    private static final Logger log = LoggerFactory.getLogger(ServiceAController.class);

    private final RestTemplate restTemplate;
    private final Tracer tracer;
    private final String serviceBUrl;

    public ServiceAController(
            RestTemplate restTemplate,
            Tracer tracer,
            @Value("${service-b.url}") String serviceBUrl) {
        this.restTemplate = restTemplate;
        this.tracer = tracer;
        this.serviceBUrl = serviceBUrl;
    }

    /**
     * Simple endpoint - just returns a message.
     * Auto-instrumentation creates an HTTP server span automatically.
     */
    @GetMapping("/hello")
    public String hello() {
        log.info("Received /hello request");
        return "Hello from Service A!";
    }

    /**
     * Calls Service B - trace context propagates automatically.
     * The RestTemplate is auto-instrumented, so it:
     * 1. Creates a client span for the outgoing HTTP call
     * 2. Injects trace context (traceparent header) into the request
     */
    @GetMapping("/call-b")
    public String callServiceB() {
        log.info("Calling Service B...");

        String response = restTemplate.getForObject(serviceBUrl + "/process", String.class);

        log.info("Service B responded: {}", response);
        return "Service A received: " + response;
    }

    /**
     * Demonstrates manual span creation for business logic.
     * Use this when you want to trace a specific operation within your code.
     */
    @GetMapping("/process/{id}")
    public String processWithManualSpan(@PathVariable String id) {
        log.info("Processing request for id={}", id);

        // Create a manual span for business logic
        Span span = tracer.spanBuilder("process-business-logic")
                .setAttribute("request.id", id)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Simulate some business logic
            String result = doBusinessLogic(id);

            // Add result info to span
            span.setAttribute("result.status", "success");

            // Call Service B within our manual span
            String serviceBResponse = restTemplate.getForObject(
                    serviceBUrl + "/validate/" + id, String.class);

            return "Processed " + id + ": " + result + " | Validated: " + serviceBResponse;
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private String doBusinessLogic(String id) {
        // Simulate work
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "computed-" + id.hashCode();
    }

    /**
     * Endpoint that simulates an error - demonstrates error tracing.
     */
    @GetMapping("/error")
    public String triggerError() {
        log.info("Triggering an error...");
        throw new RuntimeException("Intentional error for tracing demo");
    }
}
