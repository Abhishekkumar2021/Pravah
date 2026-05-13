package io.pravah.playground.saga.order;

/** In-memory order entity (no DB — focus on saga flow). */
public class Order {

    public enum Status { PENDING, INVENTORY_RESERVED, COMPLETED, CANCELLED }

    private final String id;
    private final String productId;
    private final int quantity;
    private final double amount;
    private Status status = Status.PENDING;
    private String transactionId;
    private String cancellationReason;

    public Order(String id, String productId, int quantity, double amount) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public double getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public String getCancellationReason() { return cancellationReason; }

    public void markInventoryReserved() {
        this.status = Status.INVENTORY_RESERVED;
    }

    public void complete(String transactionId) {
        this.transactionId = transactionId;
        this.status = Status.COMPLETED;
    }

    public void cancel(String reason) {
        this.cancellationReason = reason;
        this.status = Status.CANCELLED;
    }
}
