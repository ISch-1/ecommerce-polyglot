package com.ecommerce.shared.errors;

/**
 * Base for "resource not found" errors. Each module extends it with its own
 * stable code, so {@link GlobalExceptionHandler} can answer 404 without
 * knowing which module the exception comes from.
 */
public abstract class NotFoundException extends RuntimeException {

    private final String code;

    protected NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
