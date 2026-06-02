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

    // ───── 홈 대시보드 (사용자 본인) ─────

    /** 기간 내 본인 지출 합계. */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.userId = :userId AND e.spentAt >= :start AND e.spentAt < :end
        """)
    long sumAmountInRange(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    /** 최근 지출 5건. */
    List<Expense> findTop5ByUserIdOrderBySpentAtDesc(Long userId);

    /**
     * 기간 내 카테고리별 본인 지출 합계 (내림차순). 행: [categoryName, amount].
     */
    @Query(value = """
        SELECT c.name AS categoryName, COALESCE(SUM(e.amount), 0) AS amount
        FROM expenses e
        JOIN categories c ON c.id = e.category_id
        WHERE e.user_id = :userId AND e.spent_at >= :start AND e.spent_at < :end
        GROUP BY c.name
        ORDER BY amount DESC
        """, nativeQuery = true)
    List<Object[]> categoryBreakdownInRange(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}
