package com.vori.backend.expense;

import com.vori.backend.common.StatType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
        Long userId, LocalDateTime start, LocalDateTime end);

    List<Expense> findByUserIdAndCategoryId(Long userId, Long categoryId);

    List<Expense> findByUserIdAndStatType(Long userId, StatType statType);
}
