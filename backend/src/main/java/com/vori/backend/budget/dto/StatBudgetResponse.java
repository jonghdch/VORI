package com.vori.backend.budget.dto;
import com.vori.backend.common.StatType;
import java.util.Map;
public record StatBudgetResponse(String yearMonth, Map<StatType,Integer> budgets, int fixedTotal, int reserveAmount) {}
