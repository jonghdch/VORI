package com.vori.backend.ledger.dto;

import com.vori.backend.common.PaymentMethod;
import com.vori.backend.income.dto.IncomeCreateRequest;
import com.vori.backend.savings.dto.SavingCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * POST /api/ledger/entries 요청 — 가계부 작성 화면의 한 번 저장분.
 *
 * 수입·저축은 단건 API 와 같은 요청을 그대로 받는다. 지출만 따로 둔 이유는 작성 화면이
 * 새 행과 고친 행을 한 목록에 섞어 보내기 때문이다 — id 가 있으면 수정, 없으면 등록.
 */
public record LedgerSaveRequest(
        @Valid @Size(max = MAX_ROWS, message = "한 번에 저장할 수 있는 지출은 " + MAX_ROWS + "건까지입니다.")
        List<ExpenseEntry> expenses,

        @Valid @Size(max = MAX_ROWS, message = "한 번에 저장할 수 있는 수입은 " + MAX_ROWS + "건까지입니다.")
        List<IncomeCreateRequest> incomes,

        @Valid @Size(max = MAX_ROWS, message = "한 번에 저장할 수 있는 저축은 " + MAX_ROWS + "건까지입니다.")
        List<SavingCreateRequest> savings
) {
    public static final int MAX_ROWS = 100;

    public LedgerSaveRequest {
        if (expenses == null) expenses = List.of();
        if (incomes == null) incomes = List.of();
        if (savings == null) savings = List.of();
    }

    /** 지출 한 행. 검증 문구는 단건 등록(ExpenseCreateRequest)과 같다. */
    public record ExpenseEntry(
            Long id,

            @NotNull(message = "카테고리를 선택해주세요.")
            Long categoryId,

            @NotNull(message = "금액을 입력해주세요.")
            @Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
            Integer amount,

            @NotBlank(message = "지출 내역을 입력해주세요.")
            @Size(max = 100, message = "지출 내역은 최대 100자까지 입력 가능합니다.")
            String item,

            LocalDateTime spentAt,
            PaymentMethod paymentMethod
    ) {}
}
