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

    /**
     * 어드민 지출 카테고리 통계 — 카테고리별 건수·총액·신호(최종) 분포. 지출이 있는 카테고리만.
     * 행: [categoryId, categoryName, count, totalAmount, redCount, grayCount, greenCount]
     */
    @Query(value = """
        SELECT e.category_id                                            AS categoryId,
               c.name                                                   AS categoryName,
               COUNT(*)                                                 AS cnt,
               COALESCE(SUM(e.amount), 0)                               AS totalAmount,
               SUM(CASE WHEN e.signal_final = 'RED'   THEN 1 ELSE 0 END) AS redCount,
               SUM(CASE WHEN e.signal_final = 'GRAY'  THEN 1 ELSE 0 END) AS grayCount,
               SUM(CASE WHEN e.signal_final = 'GREEN' THEN 1 ELSE 0 END) AS greenCount
        FROM expenses e
        JOIN categories c ON c.id = e.category_id
        GROUP BY e.category_id, c.name
        ORDER BY totalAmount DESC
        """, nativeQuery = true)
    List<Object[]> aggregateByCategory();
}
