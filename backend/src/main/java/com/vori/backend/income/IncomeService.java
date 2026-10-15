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
        // 받은 곳을 골랐으면 본인의 쓸 수 있는 계좌·체크카드·현금인지 확인하고, 결제수단을 그 계좌에 맞춘다
        // (입출금·체크카드 → TRANSFER — 체크카드는 연결 통장으로 받는 것이라서, 현금 → CASH).
        // 화면이 보낸 값보다 계좌를 믿는다 — 지출이 카드 종류로 결제수단을 맞추는 것과 같은 규칙.
        // 그러지 않으면 "현금으로 받은 신용카드 수입" 처럼 서로 어긋난 기록이 저장된다.
        // 예적금은 결제수단 종류가 없어(null) 화면이 보낸 값을 그대로 둔다.
        PaymentMethod method = req.paymentMethod();
        if (req.paymentInstrumentId() != null) {
            PaymentInstrument account = paymentInstrumentService.requireIncomeAccount(userId, req.paymentInstrumentId());
            PaymentMethod accountMethod = account.getKind() == InstrumentKind.DEBIT
                    ? PaymentMethod.TRANSFER : account.getKind().paymentMethod();
            if (accountMethod != null) method = accountMethod;
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
