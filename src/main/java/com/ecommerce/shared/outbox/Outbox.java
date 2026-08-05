package com.ecommerce.shared.outbox;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Write-side façade domain code uses. Call {@link #stage} inside the same
 * {@code @Transactional} boundary as your domain change.
 *
 * <pre>{@code
 * @Transactional
 * public Product create(...) {
 *     Product saved = repo.save(...);
 *     outbox.stage("product", saved.id().toString(),
 *                  "ProductCreated", "catalog.products",
 *                  new ProductCreated(saved.id(), ...));
 *     return saved;
 * }
 * }</pre>
 */
@Component
@RequiredArgsConstructor
public class Outbox {

    private static final TypeReference<Map<String, Object>> AS_MAP = new TypeReference<>() {};

    private final OutboxRepository repo;
    private final ObjectMapper mapper;

    public void stage(String aggregateType,
                      String aggregateId,
                      String eventType,
                      String topic,
                      Object payload) {
        Map<String, Object> serialized;
        try {
            serialized = mapper.convertValue(payload, AS_MAP);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Payload for event " + eventType + " is not serializable", e);
        }

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .payload(serialized)
                .attempts(0)
                .build();
        repo.save(event);
    }
}
