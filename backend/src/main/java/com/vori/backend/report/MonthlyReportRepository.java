package com.vori.backend.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    Optional<MonthlyReport> findByUserIdAndYearMonth(Long userId, String yearMonth);

    List<MonthlyReport> findByUserIdOrderByYearMonthDesc(Long userId);

    Optional<MonthlyReport> findFirstByUserIdAndReadAtIsNullOrderByYearMonthDesc(Long userId);
}
