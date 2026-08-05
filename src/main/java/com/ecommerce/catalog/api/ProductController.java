package com.ecommerce.catalog.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.ecommerce.catalog.application.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Product catalog")
public class ProductController {

    private final CatalogService catalog;

    @PostMapping
    @Operation(summary = "Create a product")
    public ResponseEntity<ProductDto> create(@Valid @RequestBody CreateProductRequest body,
                                             UriComponentsBuilder ucb) {
        var created = catalog.create(body.sku(), body.name(), body.description(),
                body.price(), body.stock(), body.categoryId());
        URI location = ucb.path("/api/v1/products/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(ProductDto.from(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a product by id")
    public ProductDto getById(@PathVariable UUID id) {
        return ProductDto.from(catalog.getById(id));
    }

    @GetMapping
    @Operation(summary = "List active products")
    public List<ProductDto> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.listActive(page, size).stream().map(ProductDto::from).toList();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update price and stock")
    public ProductDto update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest body) {
        return ProductDto.from(catalog.updatePriceAndStock(id, body.price(), body.stock()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        catalog.delete(id);
        return ResponseEntity.noContent().build();
    }
}
