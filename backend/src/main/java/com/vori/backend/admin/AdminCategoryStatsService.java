package com.vori.backend.admin;

import com.vori.backend.admin.dto.CategoryStatResponse;
import com.vori.backend.expense.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCategoryStatsService {

    private final ExpenseRepository expenseRepository;

    /** 카테고리별 지출 통계 (총액 내림차순). 지출이 있는 카테고리만. */
    @Transactional(readOnly = true)
    public List<CategoryStatResponse> getCategoryStats() {
        return expenseRepository.aggregateByCategory().stream()
                .map(row -> {
                    long count = ((Number) row[2]).longValue();
                    long totalAmount = ((Number) row[3]).longValue();
                    return new CategoryStatResponse(
                            ((Number) row[0]).longValue(),
                            (String) row[1],
                            count,
                            totalAmount,
                            count > 0 ? totalAmount / count : 0,
                            ((Number) row[4]).longValue(),
                            ((Number) row[5]).longValue(),
                            ((Number) row[6]).longValue()
                    );
                })
                .toList();
    }
}
