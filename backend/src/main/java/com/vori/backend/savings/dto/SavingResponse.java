package com.vori.backend.savings.dto;

import com.vori.backend.savings.Saving;
import com.vori.backend.savings.SavingType;

import java.time.LocalDate;

public record SavingResponse(
        Long id,
        LocalDate savedAt,
        Integer amount,
        String item,
        SavingType savingType,
        String note
) {
    public static SavingResponse from(Saving s) {
        return new SavingResponse(
                s.getId(),
                s.getSavedAt(),
                s.getAmount(),
                s.getItem(),
                s.getSavingType(),
                s.getNote()
        );
    }
}
