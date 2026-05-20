package com.vori.backend.expense.dto;

import com.vori.backend.common.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ExpenseCreateRequest(
        @NotNull(message = "카테고리를 선택해주세요.")
        Long categoryId,

        @NotNull(message = "금액을 입력해주세요.")
        @Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
        Integer amount,

        @NotBlank(message = "지출 내역을 입력해주세요.")
        @Size(max = 100, message = "지출 내역은 최대 100자까지 입력 가능합니다.")
        String item,

        // 프론트엔드에서 누락할 수 있으므로 @NotNull은 빼고 아래 생성자에서 처리합니다.
        LocalDateTime spentAt,
        Boolean timeProvided, // null 체크를 위해 primitive(boolean) 대신 wrapper(Boolean) 사용

        // V2 표준 가계부 필드 — 모두 선택 (NULL/false 가능)
        PaymentMethod paymentMethod,

        @Size(max = 200, message = "메모는 최대 200자까지 입력 가능합니다.")
        String memo,

        Boolean isRecurring
) {

    public ExpenseCreateRequest {
        if (timeProvided == null) {
            timeProvided = (spentAt != null);
        }
        if (spentAt == null) {
            spentAt = LocalDateTime.now();
        }
        if (isRecurring == null) {
            isRecurring = false;
        }
    }
}