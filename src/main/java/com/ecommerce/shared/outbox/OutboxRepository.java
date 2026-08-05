package com.ecommerce.shared.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the next batch of unpublished events for the current transaction.
     * {@code FOR UPDATE SKIP LOCKED} lets multiple publisher pods poll safely
     * without stepping on each other.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(@Param("batchSize") int batchSize);

    List<OutboxEvent> findFirst100ByPublishedAtIsNullOrderByCreatedAtAsc(Limit limit);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.publishedAt = :now WHERE o.id = :id")
    void markPublished(@Param("id") UUID id, @Param("now") Instant now);

    long countByPublishedAtIsNull();
}
