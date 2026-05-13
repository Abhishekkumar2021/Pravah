package io.pravah.playground.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business service that writes an {@link Order} and an {@link OutboxEvent} in the same transaction.
 * This is the core of the Outbox Pattern (ADR-004): no dual write.
 */
@Service
public class OrderService {

    private static final String TOPIC = "orders.events";
    private final OrderRepository orders;
    private final OutboxEventRepository outbox;
    private final ObjectMapper json;

    public OrderService(OrderRepository orders, OutboxEventRepository outbox, ObjectMapper json) {
        this.orders = orders;
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional
    public Order createOrder(String product, int quantity) {
        Order order = orders.save(new Order(product, quantity));
        outbox.save(buildEvent(order, "order.created"));
        return order;
    }

    @Transactional
    public Order confirmOrder(Order order) {
        order.confirm();
        orders.save(order);
        outbox.save(buildEvent(order, "order.confirmed"));
        return order;
    }

    private OutboxEvent buildEvent(Order order, String eventType) {
        try {
            String payload = json.writeValueAsString(Map.of(
                    "orderId", order.getId().toString(),
                    "product", order.getProduct(),
                    "quantity", order.getQuantity(),
                    "status", order.getStatus().name()));
            return new OutboxEvent(
                    order.getId().toString(),
                    eventType,
                    payload,
                    TOPIC,
                    order.getId().toString());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
