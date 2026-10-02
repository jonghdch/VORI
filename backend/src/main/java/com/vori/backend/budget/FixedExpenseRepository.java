package com.vori.backend.budget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FixedExpenseRepository extends JpaRepository<FixedExpense, Long> { List<FixedExpense> findByUserIdOrderByIdAsc(Long userId); }
