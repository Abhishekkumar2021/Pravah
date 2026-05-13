package io.pravah.playground.keda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka consumer that processes jobs.
 * 
 * KEDA monitors this consumer group's lag and scales the deployment accordingly.
 * When lag > lagThreshold per pod, KEDA creates more pods.
 * When lag drops, KEDA scales down (after cooldownPeriod).
 */
@Component
public class JobConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobConsumer.class);

    private final AtomicLong processedCount = new AtomicLong(0);

    @Value("${app.processing-delay-ms:500}")
    private long processingDelayMs;

    @KafkaListener(topics = "${app.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        long count = processedCount.incrementAndGet();

        log.info("[Job #{}] Received: {}", count, message);

        try {
            Thread.sleep(processingDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[Job #{}] Processed successfully", count);
    }

    public long getProcessedCount() {
        return processedCount.get();
    }
}
