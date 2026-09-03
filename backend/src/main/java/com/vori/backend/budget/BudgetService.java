package com.vori.backend.budget;

import com.vori.backend.budget.dto.BudgetResponse;
import com.vori.backend.budget.dto.BudgetUpsertRequest;
import com.vori.backend.expense.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 월 지출 예산.
 *
 * 사용액은 별도로 들고 있지 않고 조회 시점에 expenses 에서 합산한다 — 예산 테이블에 누적
 * 컬럼을 두면 지출 수정·삭제 때마다 같이 맞춰야 하고, 어긋나면 되돌릴 방법이 없다.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final ExpenseRepository expenseRepository;

    /** 해당 월 예산 + 사용 현황. 예산 미설정이어도 404 가 아니라 미설정 상태로 돌려준다. */
    @Transactional(readOnly = true)
    public BudgetResponse get(Long userId, String yearMonth) {
        validateFormat(yearMonth);
        long spent = sumSpent(userId, yearMonth);
        return monthlyBudgetRepository.findByUserIdAndYearMonth(userId, yearMonth)
                .map(b -> BudgetResponse.of(b, spent))
                .orElseGet(() -> BudgetResponse.unset(yearMonth, spent));
    }

    /** 설정 또는 변경. 월당 1건이라 같은 달로 다시 보내면 금액만 바뀐다. */
    @Transactional
    public BudgetResponse upsert(Long userId, BudgetUpsertRequest req) {
        MonthlyBudget budget = monthlyBudgetRepository
                .findByUserIdAndYearMonth(userId, req.yearMonth())
                .map(existing -> {
                    existing.updateAmount(req.amount());
                    return existing;
                })
                .orElseGet(() -> monthlyBudgetRepository.save(MonthlyBudget.builder()
                        .userId(userId)
                        .yearMonth(req.yearMonth())
                        .amount(req.amount())
                        .build()));

        return BudgetResponse.of(budget, sumSpent(userId, req.yearMonth()));
    }

    @Transactional
    public void delete(Long userId, String yearMonth) {
        validateFormat(yearMonth);
        monthlyBudgetRepository.findByUserIdAndYearMonth(userId, yearMonth)
                .ifPresent(monthlyBudgetRepository::delete);
    }

    // ───── 내부 ─────

    /** 그 달 지출 합계. 기존 홈 대시보드용 쿼리를 그대로 쓴다. */
    private long sumSpent(Long userId, String yearMonth) {
        YearMonth ym = parse(yearMonth);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        return expenseRepository.sumAmountInRange(userId, start, end);
    }

    private YearMonth parse(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "예산 월은 YYYY-MM 형식이어야 합니다");
        }
    }

    /** 조회·삭제는 @Valid 가 걸리지 않는 경로 파라미터라 여기서 검사한다. */
    private void validateFormat(String yearMonth) {
        parse(yearMonth);
    }
}
