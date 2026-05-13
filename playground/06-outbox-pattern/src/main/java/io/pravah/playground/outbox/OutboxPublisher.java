package io.pravah.playground.outbox;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls {@code outbox_events}, publishes to Kafka, marks {@code published_at}. ADR-004 polling publisher.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;

    @Value("${outbox.batch-size:50}")
    private int batchSize;

    @Value("${outbox.max-retries:10}")
    private int maxRetries;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }

    @Scheduled(fixedRateString = "${outbox.poll-interval-ms:100}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = repository.findUnpublishedBatch(batchSize, maxRetries);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Outbox poll: {} events", batch.size());
        for (OutboxEvent event : batch) {
            try {
                kafka.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .get(); // block to confirm Kafka ack before marking published
                event.markPublished();
                log.debug("Published outbox event {} to {}", event.getId(), event.getTopic());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during Kafka send", e);
            } catch (ExecutionException e) {
                log.warn("Kafka send failed for outbox event {}, retry {}", event.getId(), event.getRetryCount(), e);
                event.incrementRetry();
            }
        }
    }
}
