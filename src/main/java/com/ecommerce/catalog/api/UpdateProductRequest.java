package com.ecommerce.catalog.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;

public record UpdateProductRequest(
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Min(0)                    int stock
) {}
