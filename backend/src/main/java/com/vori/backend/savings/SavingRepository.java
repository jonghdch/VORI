package com.vori.backend.savings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SavingRepository extends JpaRepository<Saving, Long> {

    List<Saving> findByUserIdAndSavedAtOrderByIdAsc(Long userId, LocalDate savedAt);
}
