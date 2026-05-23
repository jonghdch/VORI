package com.vori.backend.savings.dto;

import com.vori.backend.savings.SavingType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SavingCreateRequest(
        @NotNull(message = "저축 종류를 선택해주세요.")
        SavingType savingType,

        @NotNull(message = "금액을 입력해주세요.")
        @Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
        Integer amount,

        @NotBlank(message = "저축 내역을 입력해주세요.")
        @Size(max = 100, message = "내역은 최대 100자까지 입력 가능합니다.")
        String item,

        LocalDate savedAt,

        @Size(max = 200, message = "메모는 최대 200자까지 입력 가능합니다.")
        String note
) {
    public SavingCreateRequest {
        if (savedAt == null) savedAt = LocalDate.now();
    }
}
