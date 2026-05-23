package com.vori.backend.savings;

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
        Saving saving = Saving.builder()
                .userId(userId)
                .savedAt(req.savedAt())
                .amount(req.amount())
                .item(req.item())
                .savingType(req.savingType())
                .note(req.note())
                .build();
        Saving saved = savingRepository.save(saving);
        return SavingResponse.from(saved);
    }
}
