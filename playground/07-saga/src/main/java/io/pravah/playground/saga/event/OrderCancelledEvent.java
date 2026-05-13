package io.pravah.playground.saga.event;

/** Saga failure: a step failed, compensation was triggered, order is cancelled. */
public record OrderCancelledEvent(
        String orderId,
        String reason
) implements SagaEvent {}
