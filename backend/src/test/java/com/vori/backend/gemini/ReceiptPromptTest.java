package com.vori.backend.gemini;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 영수증·결제 캡처 인식 프롬프트 구성 검증. 실제 인식 품질은 개인정보 없는 가짜 캡처로 로컬에서 확인했다(PR 본문 참조).
 */
class ReceiptPromptTest {

    @Test
    @DisplayName("영수증 프롬프트는 결제 캡처 종류를 받고, 오늘 날짜로 연도 없는 결제 문자를 보완한다")
    void receiptPromptCoversPaymentCaptures() {
        String prompt = GeminiClient.receiptPrompt(LocalDate.of(2026, 10, 1));

        assertThat(prompt).contains("CARD_ALERT", "PAYMENT_SCREEN", "TRANSFER", "MULTIPLE", "NOT_PAYMENT");
        assertThat(prompt).contains("오늘은 2026-10-01 입니다");
        // 여러 건 목록에서 아무거나 골라 채우지 않게 한다
        assertThat(prompt).contains("여러 건 중 하나를 골라 채우지 마세요");
        // 이체는 받는 사람 이름보다 메모가 가계부 항목으로 맞다
        assertThat(prompt).contains("이체면 메모");
    }
}
