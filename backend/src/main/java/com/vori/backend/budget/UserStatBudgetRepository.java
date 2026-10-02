package com.vori.backend.budget;
import com.vori.backend.common.StatType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserStatBudgetRepository extends JpaRepository<UserStatBudget, Long> {
    List<UserStatBudget> findByUserIdAndYearMonth(Long userId, String yearMonth);
    Optional<UserStatBudget> findByUserIdAndYearMonthAndStatType(Long userId, String yearMonth, StatType statType);
}
