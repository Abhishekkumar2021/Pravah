package io.pravah.playground.saga;

/** Kafka topic names for saga events — single source of truth. */
public final class SagaTopics {
    public static final String ORDER_CREATED = "saga.order.created";
    public static final String INVENTORY_RESERVED = "saga.inventory.reserved";
    public static final String INVENTORY_RESERVATION_FAILED = "saga.inventory.reservation.failed";
    public static final String PAYMENT_PROCESSED = "saga.payment.processed";
    public static final String PAYMENT_FAILED = "saga.payment.failed";
    public static final String ORDER_COMPLETED = "saga.order.completed";
    public static final String ORDER_CANCELLED = "saga.order.cancelled";

    private SagaTopics() {}
}
