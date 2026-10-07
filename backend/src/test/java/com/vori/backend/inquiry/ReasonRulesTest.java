package com.vori.backend.inquiry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** AI 분류가 실패했을 때 쓰는 낱말 규칙 — 잘못 낮추느니 기타(신호 그대로)로 둔다. */
class ReasonRulesTest {

    @Test
    @DisplayName("자주 나오는 답변은 낱말로 사유를 고른다")
    void classifiesCommonAnswers() {
        assertThat(ReasonRules.classify("친구 결혼식이라 축의금 냈어")).isEqualTo(ReasonCategory.CEREMONY);
        assertThat(ReasonRules.classify("갑자기 아파서 병원 갔어")).isEqualTo(ReasonCategory.EMERGENCY);
        assertThat(ReasonRules.classify("토익 교재 샀어")).isEqualTo(ReasonCategory.SELF_INVEST);
        assertThat(ReasonRules.classify("동기들이랑 회식")).isEqualTo(ReasonCategory.SOCIAL);
        assertThat(ReasonRules.classify("그냥 갖고 싶어서")).isEqualTo(ReasonCategory.IMPULSE);
    }

    @Test
    @DisplayName("사람 만남 낱말이 같이 와도 경조사·갑작스러운 일을 먼저 본다")
    void specificReasonBeatsCompany() {
        assertThat(ReasonRules.classify("친구랑 같이 병원")).isEqualTo(ReasonCategory.EMERGENCY);
        assertThat(ReasonRules.classify("선배 결혼식")).isEqualTo(ReasonCategory.CEREMONY);
    }

    @Test
    @DisplayName("「사고 싶어서」는 사고(사건)가 아니다")
    void wantToBuyIsNotAccident() {
        assertThat(ReasonRules.classify("너무 사고 싶어서 샀어")).isEqualTo(ReasonCategory.IMPULSE);
        assertThat(ReasonRules.classify("교통사고 나서 택시")).isEqualTo(ReasonCategory.EMERGENCY);
    }

    @Test
    @DisplayName("「안과」「안내」처럼 「안」으로 시작하는 낱말은 부정이 아니다")
    void wordsStartingWithAnAreNotNegation() {
        assertThat(ReasonRules.classify("안과 진료 받았어")).isEqualTo(ReasonCategory.EMERGENCY);
        assertThat(ReasonRules.classify("병원 안내 받고 약국")).isEqualTo(ReasonCategory.EMERGENCY);
    }

    @Test
    @DisplayName("부정 표현이 있거나 모르는 말이면 기타 — 신호를 낮추지 않는다")
    void negationOrUnknownIsEtc() {
        assertThat(ReasonRules.classify("병원 갈 뻔했는데 안 갔어")).isEqualTo(ReasonCategory.ETC);
        assertThat(ReasonRules.classify("결혼식은 아니고 그냥")).isEqualTo(ReasonCategory.ETC);
        assertThat(ReasonRules.classify("병원 안갔어")).isEqualTo(ReasonCategory.ETC);
        assertThat(ReasonRules.classify("학원 교재 못샀어")).isEqualTo(ReasonCategory.ETC);
        assertThat(ReasonRules.classify("학원 못 가서")).isEqualTo(ReasonCategory.ETC);
        assertThat(ReasonRules.classify("그때는 그럴 일이 있었어")).isEqualTo(ReasonCategory.ETC);
        assertThat(ReasonRules.classify("  ")).isEqualTo(ReasonCategory.ETC);
        assertThat(ReasonRules.classify(null)).isEqualTo(ReasonCategory.ETC);
    }

    @Test
    @DisplayName("모든 사유에 칩 문구가 있다")
    void everyReasonHasChipLabel() {
        Arrays.stream(ReasonCategory.values())
                .forEach(r -> assertThat(ReasonRules.CHIP_LABEL.get(r)).as(r.name()).isNotBlank());
    }
}
