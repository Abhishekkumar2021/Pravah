package io.pravah.playground.kafka.consumer;

/**
 * Thrown when a message is intentionally bad (stepId = "poison").
 *
 * The DefaultErrorHandler in KafkaConfig catches this, retries 3 times
 * with exponential backoff, then sends the message to the Dead Letter Topic.
 *
 * In real Pravah, this would be a broader exception hierarchy:
 *   - TransientException    → retry (network blip, DB connection timeout)
 *   - PermanentException    → DLQ immediately, no retry
 *   - PoisonPillException   → DLQ immediately, alert the team
 */
public class PoisonPillException extends RuntimeException {
    public PoisonPillException(String message) {
        super(message);
    }
}
