package com.vori.backend.ledger;

import com.vori.backend.category.CategoryRepository;
import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.income.IncomeRepository;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
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
    private final UserRepository userRepository;

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

    /**
     * 본인 지출만 삭제. 없으면 404, 남의 것이면 403.
     *
     * 파생 상태 처리 (도메인 규칙):
     * - user.totalSaved — 이 지출이 더했던 절약액을 되돌린다 (사용자에게 보이는 누적 수치).
     * - EMA(user_stat_stats)·펫 스탯·goal 누적 — 의도적으로 보존. EMA 는 시계열 지표라
     *   중간 항 제거가 수학적으로 불가하고, 펫 성장·goal 은 "그 시점에 일어난 이력" 으로 취급.
     */
    @Transactional
    public void deleteExpense(Long userId, Long expenseId) {
        Expense e = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지출을 찾을 수 없습니다"));
        if (!e.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 지출만 삭제할 수 있습니다");
        }

        int saved = e.getSavedAmount() == null ? 0 : e.getSavedAmount();
        if (saved > 0) {
            User user = userRepository.findById(userId).orElseThrow();
            int current = user.getTotalSaved() == null ? 0 : user.getTotalSaved();
            user.addTotalSaved(-Math.min(saved, current)); // 음수 방지 클램프
        }

        expenseRepository.delete(e);
    }
}
