package com.ecommerce.catalog.domain.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductCreated(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        int stock,
        UUID categoryId,
        Instant occurredAt
) {}
