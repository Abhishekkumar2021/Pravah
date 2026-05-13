package io.pravah.playground.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.pravah.playground.saga.inventory.InventoryService;
import io.pravah.playground.saga.order.Order;
import io.pravah.playground.saga.order.OrderService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Integration tests for choreographed saga (ADR-011).
 * Uses EmbeddedKafka — no Docker required.
 * 
 * PaymentService threshold = 400 (fails if amount > 400).
 * Amount = quantity * 100.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
                SagaTopics.ORDER_CREATED,
                SagaTopics.INVENTORY_RESERVED,
                SagaTopics.INVENTORY_RESERVATION_FAILED,
                SagaTopics.PAYMENT_PROCESSED,
                SagaTopics.PAYMENT_FAILED,
                SagaTopics.ORDER_COMPLETED,
                SagaTopics.ORDER_CANCELLED
        },
        brokerProperties = {
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1"
        }
)
class SagaChoreographyIT {

    @Autowired
    OrderService orderService;

    @Autowired
    InventoryService inventoryService;

    /**
     * Happy path: order created → inventory reserved → payment processed → order completed.
     * Order 2 WIDGETs: amount = 200 < 400 threshold → payment succeeds.
     */
    @Test
    void happyPath_orderCompletesSuccessfully() {
        int initialStock = inventoryService.getStock("WIDGET");
        Order order = orderService.createOrder("WIDGET", 2, 200.0);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Order updated = orderService.getOrder(order.getId());
            assertThat(updated.getStatus()).isEqualTo(Order.Status.COMPLETED);
            assertThat(updated.getTransactionId()).startsWith("TXN-");
        });

        assertThat(inventoryService.getStock("WIDGET")).isEqualTo(initialStock - 2);
    }

    /**
     * Failure at step 2: out of stock → order cancelled (no compensation needed).
     */
    @Test
    void inventoryFails_orderCancelled() {
        Order order = orderService.createOrder("OUT_OF_STOCK_ITEM", 5, 500.0);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Order updated = orderService.getOrder(order.getId());
            assertThat(updated.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(updated.getCancellationReason()).contains("Inventory");
        });
    }

    /**
     * Failure at step 3: payment fails → order cancelled, inventory released (compensation).
     * 
     * Order 5 GADGETs: stock = 5 (enough), amount = 500 > 400 threshold → payment fails.
     * Inventory compensation should release the reserved stock.
     */
    @Test
    void paymentFails_orderCancelledAndInventoryReleased() {
        int initialStock = inventoryService.getStock("GADGET");
        assertThat(initialStock).as("GADGET initial stock").isEqualTo(5);

        Order order = orderService.createOrder("GADGET", 5, 500.0);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Order updated = orderService.getOrder(order.getId());
            assertThat(updated.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(updated.getCancellationReason()).contains("Payment");
        });

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(inventoryService.getStock("GADGET"))
                        .as("Inventory should be released after payment failure")
                        .isEqualTo(initialStock));
    }
}
