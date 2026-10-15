package com.vori.backend.pet;

import com.vori.backend.common.PaymentMethod;
import com.vori.backend.expense.Expense;
import com.vori.backend.expense.Signal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 펫 대화에 넘기는 가계부 요약 검증. 넘기기로 한 네 가지만 들어가고
 * 메모·결제수단 같은 사적인 내용은 빠지는지 본다 — 대화마다 외부 AI 로 나가는 데이터다.
 */
class PetLedgerSummaryTest {

    private static Expense expense(String item, int amount, Signal initial, Signal fin) {
        return Expense.builder()
                .userId(1L).item(item).amount(amount)
                .memo("엄마 생일 선물 비밀")
                .paymentMethod(PaymentMethod.CREDIT)
                .signalInitial(initial).signalFinal(fin)
                .spentAt(LocalDateTime.of(2026, 10, 2, 9, 0))
                .build();
    }

    @Test
    @DisplayName("오늘 합계·건수, 카테고리 합계, 최근 항목·신호, 판정 분포를 담는다")
    void describesFourParts() {
        Expense coffee = expense("커피", 4_500, Signal.GREEN, Signal.GREEN);
        Expense jacket = expense("무신사 자켓", 89_000, Signal.RED, Signal.GRAY);
        Expense lunch = expense("점심", 9_000, null, null);

        String text = PetLedgerSummary.format(
                List.of(coffee, jacket),
                List.<Object[]>of(new Object[]{"쇼핑", 89_000L}, new Object[]{"카페", 4_500L}),
                List.of(coffee, jacket, lunch),
                List.of(coffee, jacket, lunch));

        assertThat(text)
                .contains("오늘 지출: 2건, 합계 93,500원")
                .contains("쇼핑 89,000원, 카페 4,500원")
                // 신호는 사유 반영 뒤 최종값이 우선
                .contains("커피 (초록(합리적)), 무신사 자켓 (노랑(보통)), 점심 (판정 전)")
                .contains("초록 1건, 노랑 1건, 주황 0건, 판정 전 1건");
    }

    @Test
    @DisplayName("메모·결제수단은 넘기지 않는다")
    void leavesOutPrivateFields() {
        Expense e = expense("커피", 4_500, Signal.GREEN, Signal.GREEN);

        String text = PetLedgerSummary.format(List.of(e), List.of(), List.of(e), List.of(e));

        assertThat(text).doesNotContain("엄마 생일", "CREDIT", "신용");
    }

    @Test
    @DisplayName("기록이 없으면 없다고 적는다 — 펫이 금액을 지어내지 않게")
    void emptyLedger() {
        String text = PetLedgerSummary.format(List.of(), List.of(), List.of(), List.of());

        assertThat(text).contains("오늘 지출: 아직 없음", "카테고리별 합계: 없음", "최근 지출(최신순): 없음", "판정 분포: 없음");
    }
}
