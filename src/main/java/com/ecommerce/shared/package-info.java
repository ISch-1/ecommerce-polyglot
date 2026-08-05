/**
 * Shared kernel: infrastructure reused by every bounded context.
 *
 * <p>Contains the transactional outbox, error handling, structured logging,
 * and Jackson configuration. Nothing in {@code shared} may depend on any
 * bounded context.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Shared"
)
package com.ecommerce.shared;
