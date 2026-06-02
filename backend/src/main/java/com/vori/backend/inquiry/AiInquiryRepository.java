package com.vori.backend.inquiry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiInquiryRepository extends JpaRepository<AiInquiry, Long> {

    Optional<AiInquiry> findByExpenseId(Long expenseId);

    // 어드민 AI 대사 로그 — 최근 질문순 페이지네이션 (+ reason 필터)
    Page<AiInquiry> findByReasonCategory(ReasonCategory reasonCategory, Pageable pageable);

    long countByUserIdAndReasonCategoryAndAnsweredAtBetween(
        Long userId, ReasonCategory reasonCategory, LocalDateTime start, LocalDateTime end);

    /**
     * 특정 날짜에 지출된 expense 중 사용자 미답변 inquiry 목록.
     * Step 2 (소비 분석) 화면에서 보여줄 질문들.
     */
    @Query(value = """
        SELECT i.* FROM ai_inquiries i
        JOIN expenses e ON i.expense_id = e.id
        WHERE i.user_id = :userId
          AND i.answered_at IS NULL
          AND e.spent_at >= :start
          AND e.spent_at < :end
        ORDER BY i.asked_at ASC
        """, nativeQuery = true)
    List<AiInquiry> findPendingByDate(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
