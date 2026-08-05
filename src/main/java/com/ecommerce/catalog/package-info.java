/**
 * Catalog bounded context.
 *
 * <p>Owns product and category data. Primary store: Postgres. Read cache: Redis.
 * Publishes {@code ProductCreated}, {@code ProductUpdated}, {@code ProductDeleted}
 * via the transactional outbox.
 *
 * <p>External API surface is limited to {@link com.ecommerce.catalog.api}
 * (REST) and {@link com.ecommerce.catalog.domain.events} (domain events other
 * modules may consume). {@code application} and {@code infrastructure} are
 * private to this module — Spring Modulith enforces the boundary in tests.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Catalog"
)
package com.ecommerce.catalog;
