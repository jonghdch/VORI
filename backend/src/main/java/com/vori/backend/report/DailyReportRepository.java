package com.vori.backend.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    Optional<DailyReport> findByUserIdAndReportDate(Long userId, LocalDate reportDate);

    List<DailyReport> findByUserIdAndReportDateBetweenOrderByReportDateDesc(
        Long userId, LocalDate start, LocalDate end);
}
