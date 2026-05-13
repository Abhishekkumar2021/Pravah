package io.pravah.playground.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetch unpublished events oldest-first, locking rows to avoid duplicate publish from concurrent
     * publisher instances. {@code SKIP LOCKED} ensures other instances proceed without blocking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // SKIP LOCKED
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.publishedAt IS NULL AND e.retryCount < :maxRetries
        ORDER BY e.createdAt ASC
        LIMIT :batchSize
        """)
    List<OutboxEvent> findUnpublishedBatch(int batchSize, int maxRetries);
}
