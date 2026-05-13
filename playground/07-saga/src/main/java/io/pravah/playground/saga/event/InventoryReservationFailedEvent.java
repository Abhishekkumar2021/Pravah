package io.pravah.playground.saga.event;

/** Step 2 failure: Inventory Service could not reserve stock (out of stock). */
public record InventoryReservationFailedEvent(
        String orderId,
        String productId,
        String reason
) implements SagaEvent {}
