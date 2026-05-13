package io.pravah.playground.saga.inventory;

import io.pravah.playground.saga.SagaTopics;
import io.pravah.playground.saga.event.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Inventory Service: saga participant #2. Reserves stock on {@link OrderCreatedEvent},
 * releases stock on {@link OrderCancelledEvent} (compensation).
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** Reservation details needed for compensation. */
    private record Reservation(String productId, int quantity) {}

    private final Map<String, Integer> stock = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final KafkaTemplate<String, Object> kafka;

    public InventoryService(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
        stock.put("WIDGET", 10);
        stock.put("GADGET", 5);
        stock.put("OUT_OF_STOCK_ITEM", 0);
    }

    /** Saga Step 2: reserve inventory for the order. */
    @KafkaListener(topics = SagaTopics.ORDER_CREATED, groupId = "inventory-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        if (reservations.containsKey(event.orderId())) {
            log.info("[InventoryService] Ignoring duplicate OrderCreatedEvent for {}", event.orderId());
            return;
        }

        int available = stock.getOrDefault(event.productId(), 0);
        if (available >= event.quantity()) {
            stock.put(event.productId(), available - event.quantity());
            reservations.put(event.orderId(), new Reservation(event.productId(), event.quantity()));
            log.info("[InventoryService] Reserved {} x {} for order {}", event.quantity(), event.productId(), event.orderId());

            kafka.send(SagaTopics.INVENTORY_RESERVED, event.orderId(),
                    new InventoryReservedEvent(event.orderId(), event.productId(), event.quantity()));
        } else {
            log.warn("[InventoryService] Insufficient stock for {} (need {}, have {})",
                    event.productId(), event.quantity(), available);

            kafka.send(SagaTopics.INVENTORY_RESERVATION_FAILED, event.orderId(),
                    new InventoryReservationFailedEvent(event.orderId(), event.productId(),
                            "Insufficient stock: need " + event.quantity() + ", have " + available));
        }
    }

    /** Compensation: release reserved inventory when order is cancelled. */
    @KafkaListener(topics = SagaTopics.ORDER_CANCELLED, groupId = "inventory-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        Reservation res = reservations.remove(event.orderId());
        if (res == null) {
            log.info("[InventoryService] No reservation to release for {}", event.orderId());
            return;
        }
        stock.merge(res.productId(), res.quantity(), Integer::sum);
        log.info("[InventoryService] Released {} x {} for cancelled order {}",
                res.quantity(), res.productId(), event.orderId());
    }

    public int getStock(String productId) {
        return stock.getOrDefault(productId, 0);
    }
}
