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

    @jakarta.annotation.PostConstruct
    void logKeyStatus() {
        int n = apiKey == null ? 0 : apiKey.length();
        log.info("[Gemini] api key loaded — length={} (값 자체는 로그 X)", n);
    }

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private static final String EMBED_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=";

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

    /**
     * 텍스트 → 임베딩 벡터 (768차원, text-embedding-004).
     * 카테고리 자동 분류용 — 카테고리 leaf 와 사용자 입력을 같은 벡터 공간에서 비교.
     */
    @SuppressWarnings("unchecked")
    public double[] embed(String text) {
        String url = EMBED_URL + apiKey;
        Map<String, Object> body = Map.of(
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );
        try {
            Map<?, ?> response = restTemplate.postForObject(url, body, Map.class);
            Map<?, ?> embedding = (Map<?, ?>) response.get("embedding");
            List<Number> values = (List<Number>) embedding.get("values");
            double[] vec = new double[values.size()];
            for (int i = 0; i < vec.length; i++) vec[i] = values.get(i).doubleValue();
            return vec;
        } catch (Exception e) {
            log.error("Gemini embedding 호출 실패 — text={}", text, e);
            throw new RuntimeException("AI 분류 서비스 호출에 실패했습니다.");
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
