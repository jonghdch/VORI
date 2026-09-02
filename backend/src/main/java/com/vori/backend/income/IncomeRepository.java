package com.vori.backend.income;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    /** 해당 날짜에 수입을 남긴 사용자 id — 일일 리포트 배치 대상 선별용. */
    @Query("SELECT DISTINCT i.userId FROM Income i WHERE i.receivedAt = :date")
    List<Long> findUserIdsWithIncomeOn(@Param("date") LocalDate date);

    List<Income> findByUserIdAndReceivedAtBetweenOrderByReceivedAtDesc(
        Long userId, LocalDate start, LocalDate end);

    List<Income> findByUserIdAndReceivedAtOrderByIdAsc(Long userId, LocalDate receivedAt);

    List<Income> findByUserIdAndSource(Long userId, IncomeSource source);

    List<Income> findByUserIdAndReceivedAtBetween(Long userId, LocalDate start, LocalDate end);
}
