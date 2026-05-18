package com.vori.backend.inquiry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiInquiryRepository extends JpaRepository<AiInquiry, Long> {

    Optional<AiInquiry> findByExpenseId(Long expenseId);

    long countByUserIdAndReasonCategoryAndAnsweredAtBetween(
        Long userId, ReasonCategory reasonCategory, LocalDateTime start, LocalDateTime end);
}
