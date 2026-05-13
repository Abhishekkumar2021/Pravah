package io.pravah.playground.saga.event;

/** Step 3 failure: Payment Service failed to charge (insufficient funds, etc.). */
public record PaymentFailedEvent(
        String orderId,
        String reason
) implements SagaEvent {}
