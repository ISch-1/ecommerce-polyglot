package com.ecommerce.shared.errors;

import java.time.Instant;
import java.util.List;

import com.ecommerce.catalog.domain.ProductNotFoundException;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final Tracer tracer;

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> productNotFound(ProductNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "product.not_found", e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        List<ApiError.FieldError> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "validation.failed", "Request body failed validation", details);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "data.conflict",
                "The request violates a data integrity constraint", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unhandled(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal.error",
                "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           List<ApiError.FieldError> details) {
        String traceId = tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId()
                : null;
        ApiError body = new ApiError(status.value(), code, message, details, traceId, Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}
