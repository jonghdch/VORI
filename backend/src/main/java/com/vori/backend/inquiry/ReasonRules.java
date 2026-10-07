package com.vori.backend.inquiry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 사유 칩 문구와, AI 분류가 실패했을 때 쓰는 낱말 규칙.
 *
 * <p>Gemini 무료 한도가 끝나도 답변은 저장돼야 한다 — 「이유를 묻고 인정하는」 흐름이 멈추면 안 된다.
 * 그래서 AI 가 실패하면 답변 글의 낱말로 사유를 고른다. 이 규칙은 <b>보수적</b>이다: 잘못 걸려 신호가 부당하게
 * 낮아지는 것보다 못 낮추는 쪽(기타 = 신호 그대로)이 낫다. 부정 표현(「갈 뻔했는데」「안 갔어」「안갔어」)이 있으면 기타로 둔다.
 *
 * <p>위에서부터 처음 맞는 사유를 쓴다. 「친구 결혼식」처럼 사람 만남 낱말이 같이 오는 경우가 많아 경조사·갑작스러운
 * 일·배움을 먼저 본다. 「사고」는 「사고 싶어서」(사고 싶은 물건)와 겹쳐 「교통사고」「사고가 나」처럼만 둔다.
 */
final class ReasonRules {

    private ReasonRules() {}

    /** 칩 문구 — 칩만 고르고 글을 안 쓰면 이 문구를 답변으로 저장한다(가계부 「소비 사유」에 보인다). 화면 칩과 같다. */
    static final Map<ReasonCategory, String> CHIP_LABEL = Map.of(
            ReasonCategory.CEREMONY, "경조사",
            ReasonCategory.EMERGENCY, "갑작스러운 일",
            ReasonCategory.SOCIAL, "모임·만남",
            ReasonCategory.SELF_INVEST, "배움·자기계발",
            ReasonCategory.IMPULSE, "사고 싶어서",
            ReasonCategory.ETC, "기타");

    private static final List<String> NEGATIONS = List.of("뻔", "않", "아니", "안 ", "못 ");
    /** 붙여 쓴 부정(「안갔어」「못샀어」). 낱말 첫머리에서만 본다 — 「안과」「편안」「안내」는 걸리지 않게. */
    private static final Pattern ATTACHED_NEGATION =
            Pattern.compile("(^|\\s)(안|못)(가|갔|사|샀|하|해|했|먹|냈|썼|써|와|왔)");

    private static final Map<ReasonCategory, List<String>> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put(ReasonCategory.CEREMONY, List.of(
                "결혼", "축의", "장례", "조의", "부의", "돌잔치", "경조사", "제사", "빈소", "상견례"));
        WORDS.put(ReasonCategory.EMERGENCY, List.of(
                "병원", "응급", "약국", "약값", "치료", "수술", "입원", "진료", "교통사고", "사고가", "사고 났", "사고났",
                "수리", "고장", "분실", "다쳐", "다쳤", "아파", "아팠"));
        WORDS.put(ReasonCategory.SELF_INVEST, List.of(
                "학원", "강의", "수강", "교재", "자격증", "시험", "토익", "공부", "인강", "등록금", "수업", "전공책", "문제집"));
        WORDS.put(ReasonCategory.SOCIAL, List.of(
                "친구", "모임", "회식", "동아리", "엠티", "뒤풀이", "데이트", "동기", "선배", "후배", "약속", "만나"));
        WORDS.put(ReasonCategory.IMPULSE, List.of(
                "갖고 싶", "가지고 싶", "사고 싶", "충동", "세일", "할인", "스트레스", "지름"));
    }

    /** 답변 글 → 사유. 비었거나, 부정 표현이 있거나, 맞는 낱말이 없으면 기타. */
    static ReasonCategory classify(String answer) {
        if (answer == null || answer.isBlank()) return ReasonCategory.ETC;
        String text = answer.toLowerCase(Locale.ROOT);
        if (NEGATIONS.stream().anyMatch(text::contains) || ATTACHED_NEGATION.matcher(text).find()) {
            return ReasonCategory.ETC;
        }
        for (Map.Entry<ReasonCategory, List<String>> e : WORDS.entrySet()) {
            if (e.getValue().stream().anyMatch(text::contains)) return e.getKey();
        }
        return ReasonCategory.ETC;
    }
}
