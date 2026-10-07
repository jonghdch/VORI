package com.vori.backend.judgment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyJudgmentRepository extends JpaRepository<DailyJudgment, Long> {
    Optional<DailyJudgment> findByUserIdAndJudgmentDate(Long userId, LocalDate judgmentDate);
    List<DailyJudgment> findByUserIdAndJudgmentDateBetweenOrderByJudgmentDate(Long userId, LocalDate from, LocalDate to);
    /** 자정이 지나도록 확정되지 않은 판정 — JudgmentRewardSettler 가 1차 판정 내용으로 확정한다. */
    List<DailyJudgment> findByStatusAndJudgmentDateBefore(JudgmentStatus status, LocalDate date);
    /** 자정이 지났는데 보상을 아직 주지 않은 확정 판정 — JudgmentRewardSettler 가 지급한다. */
    List<DailyJudgment> findByStatusAndRewardedAtIsNullAndJudgmentDateBefore(JudgmentStatus status, LocalDate date);
}
