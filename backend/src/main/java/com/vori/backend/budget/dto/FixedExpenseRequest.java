package com.vori.backend.budget.dto;
import jakarta.validation.constraints.*;
public record FixedExpenseRequest(@NotBlank @Size(max=50) String name, @NotNull @Min(0) @Max(100_000_000) Integer amount) {}
