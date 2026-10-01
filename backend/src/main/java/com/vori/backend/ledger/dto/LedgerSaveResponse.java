package com.vori.backend.ledger.dto;

import com.vori.backend.expense.dto.ExpenseResponse;
import com.vori.backend.income.dto.IncomeResponse;
import com.vori.backend.savings.dto.SavingResponse;

import java.util.List;

/** 저장된 행들. 각 목록은 요청과 같은 순서다. */
public record LedgerSaveResponse(
        List<ExpenseResponse> expenses,
        List<IncomeResponse> incomes,
        List<SavingResponse> savings
) {}
