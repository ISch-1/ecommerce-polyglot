package com.ecommerce.catalog.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.catalog.domain.Product;

public record ProductDto(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stock,
        UUID categoryId,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    static ProductDto from(Product p) {
        return new ProductDto(p.id(), p.sku(), p.name(), p.description(),
                p.price(), p.stock(), p.categoryId(), p.active(),
                p.createdAt(), p.updatedAt());
    }
}
