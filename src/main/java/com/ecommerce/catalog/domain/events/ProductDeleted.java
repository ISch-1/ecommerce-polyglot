package com.ecommerce.catalog.domain.events;

import java.time.Instant;
import java.util.UUID;

public record ProductDeleted(
        UUID id,
        String sku,
        Instant occurredAt
) {}
