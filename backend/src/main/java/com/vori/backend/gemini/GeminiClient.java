package com.vori.backend.gemini;

import com.vori.backend.common.StatType;
import com.vori.backend.inquiry.ReasonCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final RestTemplate restTemplate;

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    public String generateQuestion(String item, int amount, BigDecimal meanEma, StatType statType) {
        String prompt = String.format(
                "당신은 사용자의 소비 습관을 분석하는 친근한 AI 어시스턴트입니다.\n" +
                        "사용자가 '%s' 항목에 %,d원을 지출했는데, 이 카테고리 평균(%.0f원)보다 높습니다.\n" +
                        "이 지출의 이유를 묻는 짧고 따뜻한 질문을 한국어로 1문장만 작성하세요. 질문만 출력하세요.",
                item, amount, meanEma
        );
        return callGemini(prompt);
    }

    public ReasonCategory classifyAnswer(String question, String answer) {
        String prompt = String.format(
                "다음 질문에 대한 사용자 답변을 카테고리 중 하나로 분류하세요.\n" +
                        "카테고리: CEREMONY(경조사), EMERGENCY(응급/긴급), SOCIAL(사교/외식), SELF_INVEST(자기투자), IMPULSE(충동구매), ETC(기타)\n" +
                        "질문: %s\n답변: %s\n" +
                        "주의사항: 절대로 부가적인 설명이나 마침표를 붙이지 말고, 오직 위 카테고리의 영문명(예: CEREMONY) 하나만 출력하세요.",
                question, answer
        );
        String result = callGemini(prompt).trim().toUpperCase();
        try {
            return ReasonCategory.valueOf(result);
        } catch (IllegalArgumentException e) {
            log.warn("Gemini 분류 실패 — ETC 처리: raw={}", result);
            return ReasonCategory.ETC;
        }
    }

    private String callGemini(String text) {
        String url = BASE_URL + apiKey;
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", text))))
        );
        try {
            Map<?, ?> response = restTemplate.postForObject(url, body, Map.class);
            return extractText(response);
        } catch (Exception e) {
            log.error("Gemini API 호출 실패", e);
            throw new RuntimeException("AI 서비스 호출에 실패했습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> response) {
        List<?> candidates = (List<?>) response.get("candidates");
        Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
        Map<?, ?> content = (Map<?, ?>) candidate.get("content");
        List<?> parts = (List<?>) content.get("parts");
        Map<?, ?> part = (Map<?, ?>) parts.get(0);
        return (String) part.get("text");
    }
}
