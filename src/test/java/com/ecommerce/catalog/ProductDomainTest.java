package com.ecommerce.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.catalog.domain.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fast, pure-Java tests for the domain type — no Spring, no containers. */
class ProductDomainTest {

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> valid("SKU-1", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePrice() {
        assertThatThrownBy(() -> new Product(
                UUID.randomUUID(), "SKU-1", "Name", "", new BigDecimal("-1.00"),
                0, null, true, Instant.now(), Instant.now(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeStock() {
        assertThatThrownBy(() -> new Product(
                UUID.randomUUID(), "SKU-1", "Name", "", BigDecimal.ONE,
                -1, null, true, Instant.now(), Instant.now(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withStockProducesNewInstance() {
        Product p = valid("SKU-2", "Widget");
        Product changed = p.withStock(42);
        assertThat(changed.stock()).isEqualTo(42);
        assertThat(p.stock()).isEqualTo(0);
    }

    private static Product valid(String sku, String name) {
        return new Product(UUID.randomUUID(), sku, name, "", BigDecimal.ONE, 0,
                null, true, Instant.now(), Instant.now(), 0);
    }
}
