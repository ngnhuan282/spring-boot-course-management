package com.ccnlthd.comparison.controller;

import com.ccnlthd.comparison.dto.ProductRequest;
import com.ccnlthd.comparison.model.Product;
import com.ccnlthd.comparison.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> getAll() {

        return productService.getAll();
    }

    @GetMapping("/{id}")
    public Product getById(
            @PathVariable Long id
    ) {

        return productService.getById(id);
    }

    @PostMapping
    public ResponseEntity<Product> create(
            @Valid @RequestBody ProductRequest request
    ) {

        Product product =
                productService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(product);
    }

    @PutMapping("/{id}")
    public Product update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {

        return productService.update(
                id,
                request
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {

        productService.delete(id);

        return ResponseEntity
                .noContent()
                .build();
    }
}