package com.vori.backend.income;

import com.vori.backend.common.PaymentMethod;
import com.vori.backend.paymentinstrument.InstrumentKind;
import com.vori.backend.paymentinstrument.PaymentInstrument;
import com.vori.backend.paymentinstrument.PaymentInstrumentService;
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
    private final PaymentInstrumentService paymentInstrumentService;

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
        // 받은 곳을 골랐으면 본인의 쓸 수 있는 계좌·체크카드·현금인지 확인하고, 결제수단이 비어 있으면 채운다
        // (입출금·체크카드 → TRANSFER — 체크카드는 연결 통장으로 받는 것이라서, 현금 → CASH, 예적금은 해당 없음).
        PaymentMethod method = req.paymentMethod();
        if (req.paymentInstrumentId() != null) {
            PaymentInstrument account = paymentInstrumentService.requireIncomeAccount(userId, req.paymentInstrumentId());
            if (method == null) {
                method = account.getKind() == InstrumentKind.DEBIT ? PaymentMethod.TRANSFER : account.getKind().paymentMethod();
            }
        }
        Income income = Income.builder()
                .userId(userId)
                .receivedAt(req.receivedAt())
                .amount(req.amount())
                .note(req.item())
                .source(req.source())
                .paymentMethod(method)
                .paymentInstrumentId(req.paymentInstrumentId())
                .isRecurring(req.isRecurring())
                .build();
        Income saved = incomeRepository.save(income);
        return IncomeResponse.from(saved);
    }
}
