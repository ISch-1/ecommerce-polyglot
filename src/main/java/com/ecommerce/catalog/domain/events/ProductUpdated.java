package com.ecommerce.catalog.domain.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductUpdated(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        int stock,
        boolean active,
        Instant occurredAt
) {}
