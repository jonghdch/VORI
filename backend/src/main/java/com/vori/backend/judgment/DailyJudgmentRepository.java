package com.vori.backend.judgment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyJudgmentRepository extends JpaRepository<DailyJudgment, Long> {
    Optional<DailyJudgment> findByUserIdAndJudgmentDate(Long userId, LocalDate judgmentDate);
    List<DailyJudgment> findByUserIdAndJudgmentDateBetweenOrderByJudgmentDate(Long userId, LocalDate from, LocalDate to);
}
