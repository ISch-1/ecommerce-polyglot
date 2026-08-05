package com.ecommerce.catalog.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ecommerce.catalog.domain.Product;
import com.ecommerce.catalog.domain.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Anti-corruption boundary between JPA entities and the domain type.
 * The application service depends on this, never on {@link ProductEntity}.
 */
@Component
@RequiredArgsConstructor
public class ProductRepository {

    private final ProductJpaRepository jpa;

    public Product create(String sku, String name, String description,
                          BigDecimal price, int stock, UUID categoryId) {
        ProductEntity saved = jpa.save(
                ProductEntity.fromNew(sku, name, description, price, stock, categoryId));
        return saved.toDomain();
    }

    public Optional<Product> findById(UUID id) {
        return jpa.findById(id).map(ProductEntity::toDomain);
    }

    public Product getById(UUID id) {
        return findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public List<Product> listActive(int page, int size) {
        return jpa.findByActiveTrue(PageRequest.of(page, size))
                .map(ProductEntity::toDomain)
                .toList();
    }

    public Product updatePriceAndStock(UUID id, BigDecimal newPrice, int newStock) {
        ProductEntity entity = jpa.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        entity.setPrice(newPrice);
        entity.setStock(newStock);
        return jpa.save(entity).toDomain();
    }

    public void delete(UUID id) {
        if (!jpa.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        jpa.deleteById(id);
    }

    public boolean skuExists(String sku) {
        return jpa.existsBySku(sku);
    }
}
