package io.pravah.playground.saga.event;

/** Step 2 success: Inventory Service reserved stock for this order. */
public record InventoryReservedEvent(
        String orderId,
        String productId,
        int quantity
) implements SagaEvent {}
