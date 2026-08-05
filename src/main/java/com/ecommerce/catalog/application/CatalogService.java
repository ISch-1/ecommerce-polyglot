package com.ecommerce.catalog.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.ecommerce.catalog.domain.Product;
import com.ecommerce.catalog.domain.ProductNotFoundException;
import com.ecommerce.catalog.domain.events.ProductCreated;
import com.ecommerce.catalog.domain.events.ProductDeleted;
import com.ecommerce.catalog.domain.events.ProductUpdated;
import com.ecommerce.catalog.infrastructure.CacheConfig;
import com.ecommerce.catalog.infrastructure.ProductRepository;
import com.ecommerce.shared.outbox.Outbox;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the catalog.
 *
 * <ul>
 *   <li>Writes commit domain change + outbox event in a single transaction (ADR 0003).</li>
 *   <li>Reads use Redis via {@link Cacheable}; on miss, a per-JVM singleflight
 *       coalesces concurrent loaders (ADR 0004).</li>
 *   <li>Mutations {@link CacheEvict} locally; other pods invalidate on the
 *       consumed domain event.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    private static final String TOPIC = "catalog.products";
    private static final String AGGREGATE = "product";

    private final ProductRepository products;
    private final Outbox outbox;
    private final MeterRegistry meters;

    @Value("${catalog.cache.singleflight.timeout:PT2S}")
    private Duration singleflightTimeout;

    /** Per-JVM in-flight loaders keyed by product id. See ADR 0004. */
    private final ConcurrentHashMap<UUID, CompletableFuture<Product>> inFlight = new ConcurrentHashMap<>();

    @Transactional
    public Product create(String sku, String name, String description,
                          BigDecimal price, int stock, UUID categoryId) {
        if (products.skuExists(sku)) {
            throw new IllegalStateException("SKU already exists: " + sku);
        }
        Product saved = products.create(sku, name, description, price, stock, categoryId);
        outbox.stage(AGGREGATE, saved.id().toString(), "ProductCreated", TOPIC,
                new ProductCreated(saved.id(), saved.sku(), saved.name(),
                        saved.price(), saved.stock(), saved.categoryId(), Instant.now()));
        meters.counter("catalog.product.created").increment();
        return saved;
    }

    /**
     * Cache-aside read. On miss, {@link #loadWithSingleflight} coalesces
     * concurrent loaders so Postgres sees exactly one lookup per hot key.
     */
    @Cacheable(value = CacheConfig.PRODUCTS_CACHE, key = "#id", sync = false)
    public Product getById(UUID id) {
        return loadWithSingleflight(id);
    }

    private Product loadWithSingleflight(UUID id) {
        // computeIfAbsent guarantees at most one loader starts per key.
        CompletableFuture<Product> future = inFlight.computeIfAbsent(id, key -> {
            CompletableFuture<Product> f = new CompletableFuture<>();
            try {
                Product loaded = products.getById(key);
                f.complete(loaded);
            } catch (RuntimeException e) {
                f.completeExceptionally(e);
            }
            return f;
        });

        try {
            return future.get(singleflightTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Product load timed out for " + id, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading product " + id, e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new IllegalStateException("Failed to load product " + id, cause);
        } finally {
            // Always drop the future so a subsequent miss can start a fresh loader.
            inFlight.remove(id);
        }
    }

    public List<Product> listActive(int page, int size) {
        return products.listActive(page, size);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public Product updatePriceAndStock(UUID id, BigDecimal newPrice, int newStock) {
        Product updated = products.updatePriceAndStock(id, newPrice, newStock);
        outbox.stage(AGGREGATE, updated.id().toString(), "ProductUpdated", TOPIC,
                new ProductUpdated(updated.id(), updated.sku(), updated.name(),
                        updated.price(), updated.stock(), updated.active(), Instant.now()));
        meters.counter("catalog.product.updated").increment();
        return updated;
    }

    @Transactional
    @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public void delete(UUID id) {
        Product victim = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        products.delete(id);
        outbox.stage(AGGREGATE, id.toString(), "ProductDeleted", TOPIC,
                new ProductDeleted(id, victim.sku(), Instant.now()));
        meters.counter("catalog.product.deleted").increment();
    }
}
