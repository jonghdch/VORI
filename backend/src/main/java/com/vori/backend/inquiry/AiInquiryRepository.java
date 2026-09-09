package com.vori.backend.inquiry;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiInquiryRepository extends JpaRepository<AiInquiry, Long> {

    Optional<AiInquiry> findByExpenseId(Long expenseId);

    /**
     * 답변 기록 전용 조회 (SELECT ... FOR UPDATE).
     *
     * answerInquiry 의 쓰기 트랜잭션은 answeredAt 을 다시 확인해 중복 답변을 걸러내는데,
     * 일반 findById 로는 같은 질문에 동시에 온 두 요청이 둘 다 null 을 읽고 통과한다.
     * 인정 보상(코인·스탯)이 여기 걸려 있으므로 두 번 지급될 수 있다.
     * 행을 잠가 직렬화하면 뒤엣것은 앞엣것이 커밋한 answeredAt 을 보고 물러난다.
     * (UserRepository.findByIdForUpdate 와 같은 원칙)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM AiInquiry i WHERE i.id = :id")
    Optional<AiInquiry> findByIdForUpdate(@Param("id") Long id);

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
