package io.pravah.playground.saga.event;

/** Step 3 success: Payment Service charged the customer. */
public record PaymentProcessedEvent(
        String orderId,
        String transactionId,
        double amount
) implements SagaEvent {}
