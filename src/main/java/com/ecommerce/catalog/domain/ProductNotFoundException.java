package com.ecommerce.catalog.domain;

import java.util.UUID;

import com.ecommerce.shared.errors.NotFoundException;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException(UUID id) {
        super("product.not_found", "Product not found: " + id);
    }
}
