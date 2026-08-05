package com.ecommerce.catalog.api;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.*;

public record CreateProductRequest(
        @NotBlank @Size(max = 64)  String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000)          String description,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Min(0)                    int stock,
        UUID categoryId
) {}
