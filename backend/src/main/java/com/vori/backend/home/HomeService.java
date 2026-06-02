package com.vori.backend.home;

import com.vori.backend.category.CategoryRepository;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.stats.StatsService;
import com.vori.backend.stats.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final StatsService statsService;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public HomeSummaryResponse getSummary(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        StatsResponse s = statsService.getMyStats(userId);
        HomeSummaryResponse.Stats stats = new HomeSummaryResponse.Stats(
                s.energy(), s.charm(), s.iq(), s.endurance(), s.totalSaved());

        HomeSummaryResponse.Spending spending = new HomeSummaryResponse.Spending(
                expenseRepository.sumAmountInRange(userId, todayStart, todayEnd),
                expenseRepository.sumAmountInRange(userId, weekStart, todayEnd),
                expenseRepository.sumAmountInRange(userId, monthStart, todayEnd));

        Map<Long, String> categoryNames = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categoryNames.put(c.getId(), c.getName()));

        List<HomeSummaryResponse.RecentExpense> recent = expenseRepository
                .findTop5ByUserIdOrderBySpentAtDesc(userId).stream()
                .map(e -> new HomeSummaryResponse.RecentExpense(
                        e.getId(),
                        e.getItem(),
                        e.getAmount(),
                        categoryNames.getOrDefault(e.getCategoryId(), "기타"),
                        e.getSignalFinal(),
                        e.getSpentAt()))
                .toList();

        List<HomeSummaryResponse.CategorySlice> breakdown = expenseRepository
                .categoryBreakdownInRange(userId, monthStart, todayEnd).stream()
                .map(r -> new HomeSummaryResponse.CategorySlice(
                        (String) r[0], ((Number) r[1]).longValue()))
                .toList();

        return new HomeSummaryResponse(stats, spending, recent, breakdown);
    }
}
