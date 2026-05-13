package io.pravah.playground.saga.event;

/** Saga success: all steps passed, order is confirmed. */
public record OrderCompletedEvent(
        String orderId,
        String transactionId
) implements SagaEvent {}
