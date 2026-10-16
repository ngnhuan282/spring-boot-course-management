package com.ccnlthd.comparison.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductRequest(

        @NotBlank(message = "Product name is required")
        String name,

        @NotNull(message = "Product price is required")
        @PositiveOrZero(message = "Product price must be zero or positive")
        BigDecimal price

) {
}