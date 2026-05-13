package io.pravah.playground.saga.event;

/** Step 1: Order Service creates an order and publishes this event. */
public record OrderCreatedEvent(
        String orderId,
        String productId,
        int quantity,
        double amount
) implements SagaEvent {}
