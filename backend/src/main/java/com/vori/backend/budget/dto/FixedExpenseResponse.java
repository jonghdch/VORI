package com.vori.backend.budget.dto;
import com.vori.backend.budget.FixedExpense;
public record FixedExpenseResponse(Long id, String name, int amount) { public static FixedExpenseResponse from(FixedExpense f) { return new FixedExpenseResponse(f.getId(), f.getName(), f.getAmount()); } }
