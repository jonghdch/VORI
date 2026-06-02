package com.vori.backend.income;

import com.vori.backend.income.dto.IncomeCreateRequest;
import com.vori.backend.income.dto.IncomeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 수입 1건 단순 기록. 신호등·AI 분석 없음 — 들어온 값 그대로 저장.
 */
@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeRepository incomeRepository;

    @Transactional(readOnly = true)
    public List<IncomeResponse> listByDate(Long userId, LocalDate date) {
        return incomeRepository
                .findByUserIdAndReceivedAtOrderByIdAsc(userId, date)
                .stream()
                .map(IncomeResponse::from)
                .toList();
    }

    @Transactional
    public IncomeResponse createIncome(Long userId, IncomeCreateRequest req) {
        Income income = Income.builder()
                .userId(userId)
                .receivedAt(req.receivedAt())
                .amount(req.amount())
                .note(req.item())
                .source(req.source())
                .paymentMethod(req.paymentMethod())
                .isRecurring(req.isRecurring())
                .build();
        Income saved = incomeRepository.save(income);
        return IncomeResponse.from(saved);
    }
}
