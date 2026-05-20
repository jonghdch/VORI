package com.vori.backend.expense;

import com.vori.backend.common.StatType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class ExpenseAnomalyEvent {

    private final Long expenseId;
    private final Long userId;
    private final String item;
    private final Integer amount;
    private final StatType statType;
    private final BigDecimal meanEma;
    private final Signal signal;
}
