package com.ecommerce.shared.outbox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polling publisher. See ADR 0003 for the phase-2 Debezium swap.
 *
 * <p>Each tick locks a batch of pending events with {@code FOR UPDATE SKIP
 * LOCKED}, publishes each to Kafka synchronously, and marks the row published
 * only after the broker acknowledges. Failures leave the row unpublished for
 * the next tick — consumers must be idempotent, keyed by the event id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final long ACK_TIMEOUT_SECONDS = 5;

    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    @Value("${catalog.outbox.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${catalog.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = repo.lockPendingBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Publishing {} outbox events", batch.size());

        int published = 0;
        int failed = 0;
        for (OutboxEvent event : batch) {
            try {
                String value = mapper.writeValueAsString(event.getPayload());
                kafka.send(event.getTopic(), event.getAggregateId(), value)
                        .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                published++;
            } catch (JsonProcessingException e) {
                // Poisoned payload — will fail again next poll. Log loudly.
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError("Payload not serializable: " + e.getOriginalMessage());
                failed++;
                log.error("Cannot serialize outbox event {} — poison pill", event.getId(), e);
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(e.getMessage(), 500));
                failed++;
                log.warn("Failed to publish outbox event {} (attempt {}): {}",
                        event.getId(), event.getAttempts(), e.getMessage());
            }
        }

        meters.counter("outbox.events.published").increment(published);
        if (failed > 0) {
            meters.counter("outbox.events.failed").increment(failed);
        }
    }

    /** Exposed as {@code outbox_pending_events} — see the Grafana panel. */
    @Scheduled(fixedDelay = 5000L)
    public void reportPendingGauge() {
        long pending = repo.countByPublishedAtIsNull();
        meters.gauge("outbox.pending.events", pending);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
