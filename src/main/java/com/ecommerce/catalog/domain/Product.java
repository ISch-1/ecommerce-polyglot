package com.ecommerce.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain view of a product. Immutable — mutations construct a new instance.
 *
 * <p>Kept as a record on purpose: no framework annotations, no JPA, no Jackson.
 * The infrastructure layer maps to and from {@code ProductEntity}; the API
 * layer maps to and from {@code ProductDto}. Domain code depends on neither.
 */
public record Product(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stock,
        UUID categoryId,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public Product {
        if (name == null || name.isBlank())     throw new IllegalArgumentException("name must not be blank");
        if (sku == null || sku.isBlank())       throw new IllegalArgumentException("sku must not be blank");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("price must be >= 0");
        if (stock < 0)                          throw new IllegalArgumentException("stock must be >= 0");
    }

    public Product withStock(int newStock) {
        return new Product(id, sku, name, description, price, newStock, categoryId, active, createdAt, updatedAt, version);
    }

    public Product withPrice(BigDecimal newPrice) {
        return new Product(id, sku, name, description, newPrice, stock, categoryId, active, createdAt, updatedAt, version);
    }
}
