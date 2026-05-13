package io.pravah.playground.saga.payment;

import io.pravah.playground.saga.SagaTopics;
import io.pravah.playground.saga.event.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Payment Service: saga participant #3. Processes payment on {@link InventoryReservedEvent}.
 * Simulates failure for amounts > 1000 (for testing compensation).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final double FAILURE_THRESHOLD = 400.0;

    private final Set<String> processedOrders = ConcurrentHashMap.newKeySet();
    private final KafkaTemplate<String, Object> kafka;

    public PaymentService(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    /** Saga Step 3: process payment after inventory is reserved. */
    @KafkaListener(topics = SagaTopics.INVENTORY_RESERVED, groupId = "payment-service")
    public void onInventoryReserved(InventoryReservedEvent event) {
        if (processedOrders.contains(event.orderId())) {
            log.info("[PaymentService] Ignoring duplicate InventoryReservedEvent for {}", event.orderId());
            return;
        }
        processedOrders.add(event.orderId());

        double amount = estimateAmount(event.quantity());
        if (amount > FAILURE_THRESHOLD) {
            log.warn("[PaymentService] Payment FAILED for order {} (amount {} > threshold {})",
                    event.orderId(), amount, FAILURE_THRESHOLD);
            kafka.send(SagaTopics.PAYMENT_FAILED, event.orderId(),
                    new PaymentFailedEvent(event.orderId(), "Amount exceeds limit: " + amount));
            return;
        }

        String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[PaymentService] Payment PROCESSED for order {} (txn: {}, amount: {})",
                event.orderId(), txnId, amount);

        kafka.send(SagaTopics.PAYMENT_PROCESSED, event.orderId(),
                new PaymentProcessedEvent(event.orderId(), txnId, amount));
    }

    private double estimateAmount(int quantity) {
        return quantity * 100.0;
    }
}
