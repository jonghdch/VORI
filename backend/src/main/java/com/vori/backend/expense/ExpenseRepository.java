package com.vori.backend.expense;

import com.vori.backend.common.StatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
        Long userId, LocalDateTime start, LocalDateTime end);

    List<Expense> findByUserIdAndCategoryId(Long userId, Long categoryId);

    List<Expense> findByUserIdAndStatType(Long userId, StatType statType);

    /** statType 별 stat_delta 합계 + saved_amount 합계 — 홈 대시보드 스탯 위젯용. */
    @Query("""
        SELECT e.statType, COALESCE(SUM(e.statDelta), 0), COALESCE(SUM(e.savedAmount), 0)
        FROM Expense e
        WHERE e.userId = :userId
        GROUP BY e.statType
        """)
    List<Object[]> aggregateStatsByUserId(@Param("userId") Long userId);
}
