package com.ccnlthd.comparison.service;

import com.ccnlthd.comparison.dto.ProductRequest;
import com.ccnlthd.comparison.exception.ProductNotFoundException;
import com.ccnlthd.comparison.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private final List<Product> products = new ArrayList<>();

    private long nextId = 3L;

    public ProductService() {

        products.add(
                new Product(
                        1L,
                        "Laptop",
                        new BigDecimal("15000000")
                )
        );

        products.add(
                new Product(
                        2L,
                        "Mouse",
                        new BigDecimal("300000")
                )
        );
    }

    public List<Product> getAll() {
        return new ArrayList<>(products);
    }

    public Product getById(Long id) {

        return products.stream()
                .filter(product -> product.id().equals(id))
                .findFirst()
                .orElseThrow(() ->
                        new ProductNotFoundException(id)
                );
    }

    public Product create(ProductRequest request) {

        Product product = new Product(
                nextId++,
                request.name(),
                request.price()
        );

        products.add(product);

        return product;
    }

    public Product update(
            Long id,
            ProductRequest request
    ) {

        Product oldProduct = getById(id);

        Product updatedProduct = new Product(
                oldProduct.id(),
                request.name(),
                request.price()
        );

        int index = products.indexOf(oldProduct);

        products.set(index, updatedProduct);

        return updatedProduct;
    }

    public void delete(Long id) {

        Product product = getById(id);

        products.remove(product);
    }
}