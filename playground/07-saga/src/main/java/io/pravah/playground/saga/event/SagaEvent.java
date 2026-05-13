package io.pravah.playground.saga.event;

/**
 * Marker interface for all saga events. Each event carries an {@code orderId} as the
 * correlation ID — this ties all events in a saga instance together (ADR-011).
 */
public sealed interface SagaEvent permits
        OrderCreatedEvent,
        InventoryReservedEvent,
        InventoryReservationFailedEvent,
        PaymentProcessedEvent,
        PaymentFailedEvent,
        OrderCompletedEvent,
        OrderCancelledEvent {

    String orderId();
}
