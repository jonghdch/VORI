package com.vori.backend.savings;

import com.vori.backend.paymentinstrument.PaymentInstrumentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.vori.backend.savings.dto.SavingCreateRequest;
import com.vori.backend.savings.dto.SavingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SavingService {

    private final SavingRepository savingRepository;
    private final PaymentInstrumentService paymentInstrumentService;

    @Transactional(readOnly = true)
    public List<SavingResponse> listByDate(Long userId, LocalDate date) {
        return savingRepository
                .findByUserIdAndSavedAtOrderByIdAsc(userId, date)
                .stream()
                .map(SavingResponse::from)
                .toList();
    }

    @Transactional
    public SavingResponse createSaving(Long userId, SavingCreateRequest req) {
        // 모은 곳은 예적금 유형에서만, 본인의 쓸 수 있는 저축 통장만. 투자는 이번 범위에서 계좌를 고르지 않는다.
        if (req.paymentInstrumentId() != null) {
            if (req.savingType() != SavingType.DEPOSIT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "투자는 모은 곳을 고를 수 없어요.");
            }
            paymentInstrumentService.requireSavingsAccount(userId, req.paymentInstrumentId());
        }
        Saving saving = Saving.builder()
                .userId(userId)
                .savedAt(req.savedAt())
                .amount(req.amount())
                .item(req.item())
                .savingType(req.savingType())
                .paymentInstrumentId(req.paymentInstrumentId())
                .note(req.note())
                .build();
        Saving saved = savingRepository.save(saving);
        return SavingResponse.from(saved);
    }
}
