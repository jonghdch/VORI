package com.vori.backend.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdAndYearMonth(Long userId, String yearMonth);

    List<Goal> findByUserIdAndCategoryId(Long userId, Long categoryId);

    List<Goal> findByUserIdAndYearMonthAndStatus(Long userId, String yearMonth, GoalStatus status);
}
