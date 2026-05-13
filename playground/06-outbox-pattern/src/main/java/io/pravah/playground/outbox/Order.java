package io.pravah.playground.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Demo business entity — we'll write an outbox event in the same transaction as order creation. */
@Entity
@Table(name = "orders")
public class Order {

    public enum Status { CREATED, CONFIRMED, SHIPPED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String product;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.CREATED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Order() {}

    public Order(String product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }
    public String getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void confirm() { this.status = Status.CONFIRMED; }
    public void ship() { this.status = Status.SHIPPED; }
    public void cancel() { this.status = Status.CANCELLED; }
}
