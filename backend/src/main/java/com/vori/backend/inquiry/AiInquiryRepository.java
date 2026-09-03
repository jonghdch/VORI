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

    /**
     * 주어진 expense 들의 AI 질문(inquiry) 배치 조회.
     * = 예외적 지출(GRAY/RED·비반복)로 AI 판정을 거친 지출. 가계부 목록의
     * aiJudged 배지 + 소비 사유(answerText) 표시용.
     */
    List<AiInquiry> findByExpenseIdIn(List<Long> expenseIds);

    /** 답변을 마친 AI 질문 수 — 소통 관련 칭호 조건. */
    long countByUserIdAndAnsweredAtIsNotNull(Long userId);

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
