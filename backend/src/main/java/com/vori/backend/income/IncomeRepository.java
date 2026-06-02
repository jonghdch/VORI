package com.vori.backend.income;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findByUserIdAndReceivedAtBetweenOrderByReceivedAtDesc(
        Long userId, LocalDate start, LocalDate end);

    List<Income> findByUserIdAndReceivedAtOrderByIdAsc(Long userId, LocalDate receivedAt);

    List<Income> findByUserIdAndSource(Long userId, IncomeSource source);
}
