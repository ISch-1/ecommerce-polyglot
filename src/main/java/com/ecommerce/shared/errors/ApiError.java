package com.ecommerce.shared.errors;

import java.time.Instant;
import java.util.List;

/**
 * Problem+JSON-ish body. Small and specific — do not turn it into a framework.
 *
 * @param status    HTTP status echoed for API convenience
 * @param code      stable, machine-readable identifier (e.g. "product.not_found")
 * @param message   human-friendly explanation
 * @param details   field-level violations, empty when not applicable
 * @param traceId   propagated so support can jump straight to the trace in Jaeger
 * @param timestamp server-side timestamp of the error
 */
public record ApiError(
        int status,
        String code,
        String message,
        List<FieldError> details,
        String traceId,
        Instant timestamp
) {
    public record FieldError(String field, String message) {}
}
