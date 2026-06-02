package com.vori.backend.stats;

import com.vori.backend.common.StatType;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.stats.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final ExpenseRepository expenseRepository;

    /**
     * 사용자의 4 stat 누적 변동 + 총 절약액. expenses 테이블 GROUP BY.
     */
    @Transactional(readOnly = true)
    public StatsResponse getMyStats(Long userId) {
        List<Object[]> rows = expenseRepository.aggregateStatsByUserId(userId);
        int energy = 0, charm = 0, iq = 0, endurance = 0, totalSaved = 0;
        for (Object[] row : rows) {
            StatType type = (StatType) row[0];
            long delta = ((Number) row[1]).longValue();
            long saved = ((Number) row[2]).longValue();
            totalSaved += (int) saved;
            switch (type) {
                case ENERGY -> energy = (int) delta;
                case CHARM -> charm = (int) delta;
                case IQ -> iq = (int) delta;
                case ENDURANCE -> endurance = (int) delta;
            }
        }
        return new StatsResponse(energy, charm, iq, endurance, totalSaved);
    }
}
