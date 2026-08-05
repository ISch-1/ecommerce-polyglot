package com.ecommerce.catalog.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.catalog.domain.Product;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ProductEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    Product toDomain() {
        return new Product(id, sku, name, description, price, stock,
                categoryId, active, createdAt, updatedAt, version);
    }

    static ProductEntity fromNew(String sku, String name, String description,
                                 BigDecimal price, int stock, UUID categoryId) {
        return ProductEntity.builder()
                .sku(sku)
                .name(name)
                .description(description == null ? "" : description)
                .price(price)
                .stock(stock)
                .categoryId(categoryId)
                .active(true)
                .build();
    }
}
