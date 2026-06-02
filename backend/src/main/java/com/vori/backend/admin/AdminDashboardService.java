package com.vori.backend.admin;

import com.vori.backend.admin.dto.DashboardSummaryResponse;
import com.vori.backend.admin.dto.DashboardSummaryResponse.DailySignup;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.user.Role;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int TREND_DAYS = 7;

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final AiInquiryRepository aiInquiryRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();

        long totalUsers = userRepository.count();
        long newUsersToday = userRepository.countByCreatedAtGreaterThanEqual(today.atStartOfDay());
        long adminCount = userRepository.countByRole(Role.ADMIN);
        long totalSaved = userRepository.sumTotalSaved();
        long totalExpenses = expenseRepository.count();
        long totalAiInquiries = aiInquiryRepository.count();

        return new DashboardSummaryResponse(
                totalUsers,
                newUsersToday,
                adminCount,
                totalSaved,
                totalExpenses,
                totalAiInquiries,
                buildSignupTrend(today)
        );
    }

    /** 최근 TREND_DAYS 일 (오늘 포함) 의 일별 가입자 수. 가입 없는 날은 0 으로 채움. */
    private List<DailySignup> buildSignupTrend(LocalDate today) {
        LocalDate start = today.minusDays(TREND_DAYS - 1);

        Map<String, Long> byDay = new HashMap<>();
        for (Object[] row : userRepository.countSignupsByDaySince(start.atStartOfDay())) {
            byDay.put((String) row[0], ((Number) row[1]).longValue());
        }

        List<DailySignup> trend = new ArrayList<>(TREND_DAYS);
        for (int i = 0; i < TREND_DAYS; i++) {
            String date = start.plusDays(i).toString(); // yyyy-MM-dd
            trend.add(new DailySignup(date, byDay.getOrDefault(date, 0L)));
        }
        return trend;
    }
}
