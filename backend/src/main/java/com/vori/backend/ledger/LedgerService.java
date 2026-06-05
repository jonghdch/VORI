package com.vori.backend.ledger;

import com.vori.backend.category.CategoryRepository;
import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.income.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;

    /** 해당 월(yyyy-MM) 의 본인 지출+수입을 날짜 오름차순으로 병합. */
    @Transactional(readOnly = true)
    public List<LedgerResponse> getMonthly(Long userId, String yearMonth) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(yearMonth);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "yearMonth 형식은 yyyy-MM 입니다");
        }

        Map<Long, String> categoryNames = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categoryNames.put(c.getId(), c.getName()));

        List<LedgerResponse> rows = new ArrayList<>();
        expenseRepository
                .findByUserIdAndSpentAtBetween(
                        userId, ym.atDay(1).atStartOfDay(), ym.atEndOfMonth().atTime(23, 59, 59))
                .forEach(e -> rows.add(
                        LedgerResponse.expense(e, categoryNames.getOrDefault(e.getCategoryId(), "기타"))));
        incomeRepository
                .findByUserIdAndReceivedAtBetween(userId, ym.atDay(1), ym.atEndOfMonth())
                .forEach(i -> rows.add(LedgerResponse.income(i)));

        rows.sort(Comparator.comparing(LedgerResponse::date));
        return rows;
    }

    /** 본인 지출만 삭제. 없으면 404, 남의 것이면 403. */
    @Transactional
    public void deleteExpense(Long userId, Long expenseId) {
        Expense e = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지출을 찾을 수 없습니다"));
        if (!e.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 지출만 삭제할 수 있습니다");
        }
        expenseRepository.delete(e);
    }
}
