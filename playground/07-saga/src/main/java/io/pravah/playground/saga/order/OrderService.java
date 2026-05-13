package io.pravah.playground.saga.order;

import io.pravah.playground.saga.SagaTopics;
import io.pravah.playground.saga.event.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Order Service: saga initiator. Creates an order, publishes {@link OrderCreatedEvent},
 * then listens for downstream success/failure events to update order state.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final KafkaTemplate<String, Object> kafka;

    public OrderService(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    /** Saga Step 1: create order and kick off the saga. */
    public Order createOrder(String productId, int quantity, double amount) {
        String orderId = UUID.randomUUID().toString();
        Order order = new Order(orderId, productId, quantity, amount);
        orders.put(orderId, order);
        log.info("[OrderService] Created order {}", orderId);

        var event = new OrderCreatedEvent(orderId, productId, quantity, amount);
        kafka.send(SagaTopics.ORDER_CREATED, orderId, event);
        log.info("[OrderService] Published OrderCreatedEvent for {}", orderId);
        return order;
    }

    /** Inventory reserved → update local state. */
    @KafkaListener(topics = SagaTopics.INVENTORY_RESERVED, groupId = "order-service")
    public void onInventoryReserved(InventoryReservedEvent event) {
        Order order = orders.get(event.orderId());
        if (order == null || order.getStatus() != Order.Status.PENDING) {
            log.info("[OrderService] Ignoring duplicate/stale InventoryReservedEvent for {}", event.orderId());
            return;
        }
        order.markInventoryReserved();
        log.info("[OrderService] Order {} inventory reserved", event.orderId());
    }

    /** Payment succeeded → mark order completed. */
    @KafkaListener(topics = SagaTopics.PAYMENT_PROCESSED, groupId = "order-service")
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        Order order = orders.get(event.orderId());
        if (order == null || order.getStatus() != Order.Status.INVENTORY_RESERVED) {
            log.info("[OrderService] Ignoring duplicate/stale PaymentProcessedEvent for {}", event.orderId());
            return;
        }
        order.complete(event.transactionId());
        log.info("[OrderService] Order {} COMPLETED (txn: {})", event.orderId(), event.transactionId());

        kafka.send(SagaTopics.ORDER_COMPLETED, event.orderId(),
                new OrderCompletedEvent(event.orderId(), event.transactionId()));
    }

    /** Inventory reservation failed → cancel order (no compensation needed yet). */
    @KafkaListener(topics = SagaTopics.INVENTORY_RESERVATION_FAILED, groupId = "order-service")
    public void onInventoryFailed(InventoryReservationFailedEvent event) {
        Order order = orders.get(event.orderId());
        if (order == null || order.getStatus() != Order.Status.PENDING) {
            return;
        }
        order.cancel("Inventory: " + event.reason());
        log.info("[OrderService] Order {} CANCELLED (inventory failed: {})", event.orderId(), event.reason());

        kafka.send(SagaTopics.ORDER_CANCELLED, event.orderId(),
                new OrderCancelledEvent(event.orderId(), "Inventory reservation failed: " + event.reason()));
    }

    /** Payment failed → cancel order (compensation: release inventory). */
    @KafkaListener(topics = SagaTopics.PAYMENT_FAILED, groupId = "order-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        Order order = orders.get(event.orderId());
        if (order == null || order.getStatus() == Order.Status.CANCELLED) {
            return;
        }
        order.cancel("Payment: " + event.reason());
        log.info("[OrderService] Order {} CANCELLED (payment failed: {})", event.orderId(), event.reason());

        kafka.send(SagaTopics.ORDER_CANCELLED, event.orderId(),
                new OrderCancelledEvent(event.orderId(), "Payment failed: " + event.reason()));
    }

    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }
}
