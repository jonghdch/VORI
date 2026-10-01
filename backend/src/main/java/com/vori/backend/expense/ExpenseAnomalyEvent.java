package com.vori.backend.expense;

import com.vori.backend.common.StatType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class ExpenseAnomalyEvent {

    /**
     * 이 이벤트가 문구를 채울 대기 질문(ai_inquiries.id). 지출을 수정하면 옛 질문은 지워지고
     * 새 행이 생기므로, expense_id 가 아니라 이 id 로 찾아야 뒤늦게 도착한 옛 AI 문구가
     * 새 질문을 덮어쓰지 않는다.
     */
    private final Long inquiryId;
    private final Long expenseId;
    private final Long userId;
    private final String item;
    private final Integer amount;
    private final StatType statType;
    private final BigDecimal meanEma;
    private final Signal signal;
}
