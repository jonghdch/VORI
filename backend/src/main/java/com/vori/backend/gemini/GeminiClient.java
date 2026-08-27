package com.vori.backend.gemini;

import com.vori.backend.common.StatType;
import com.vori.backend.inquiry.ReasonCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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
    
    // gemini-2.0-flash 은퇴 시 구글이 후속으로 지목한 모델.
    // -latest 별칭은 은퇴 걱정이 없는 대신 어떤 모델에 붙을지 알 수 없다 — 실측에서
    // gemini-flash-latest 는 503 이 3/3, 응답이 40~58초였고 3.6-flash 는 3/3 성공에 평균 9.6초였다.
    // 지연시간을 예측할 수 있는 쪽을 택한다. 이 모델이 은퇴하면 404 본문이 다음 후속을 알려준다.
    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=";

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

    /**
     * 하루 소비 요약 → 펫이 사용자에게 말 거는 코멘트.
     * 통계 나열이 아니라 캐릭터의 말투로 뽑는 게 핵심 (VORI 의 스토리텔링 컨셉).
     *
     * @param petName 말하는 주체가 될 펫 종족명. 펫이 없으면 null.
     */
    public String generateDailyComment(String petName, int expenseTotal, int incomeTotal,
                                       int savedAmount, int statDelta) {
        String speaker = (petName == null || petName.isBlank())
                ? "사용자의 절약을 돕는 반려 캐릭터"
                : "사용자가 키우는 반려 캐릭터 '" + petName + "'";

        String prompt = String.format(
                "당신은 %s입니다.\n" +
                        "어제 사용자의 가계부 요약입니다.\n" +
                        "- 지출 합계: %,d원\n" +
                        "- 수입 합계: %,d원\n" +
                        "- 평소 소비 패턴 대비 절약액: %,d원 (음수면 평소보다 더 씀)\n" +
                        "- 그 결과 오른 내 스탯: %d\n" +
                        "이 내용을 바탕으로 사용자에게 건네는 짧은 코멘트를 한국어 2문장으로 쓰세요.\n" +
                        "친근한 말투로 칭찬이나 격려를 담고, 숫자를 그대로 나열하지 말고 이야기하듯 쓰세요. 코멘트만 출력하세요.",
                speaker, expenseTotal, incomeTotal, savedAmount, statDelta
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
            Map<?, ?> response = postWithRetry(url, body, "embed");
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
            return extractText(postWithRetry(url, body, "generateContent"));
        } catch (Exception e) {
            log.error("Gemini API 호출 실패", e);
            throw new RuntimeException("AI 서비스 호출에 실패했습니다.");
        }
    }

    // ───── 재시도 ─────

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 500;

    /**
     * Gemini POST + 일시적 장애 재시도.
     *
     * 재시도 대상은 시간이 지나면 풀릴 수 있는 것만 — 5xx(특히 503 "high demand"), 429,
     * 그리고 타임아웃·연결 실패(ResourceAccessException). 타임아웃은 HTTP 상태가 없어
     * 위 두 예외에 잡히지 않으므로 따로 명시해야 한다. 이게 빠져 있으면 응답이 조금 늦은 것만으로
     * 재시도 없이 즉시 실패한다.
     *
     * 401·403·404 같은 4xx 는 몇 번을 더 보내도 같은 답이 오므로 즉시 실패시킨다
     * (모델 은퇴로 404 가 났을 때 3배 느리게 실패하는 걸 막는다).
     *
     * 이 한 곳이 generateContent·embedContent 양쪽을 모두 덮는다. 특히 부팅 시
     * CategorizeService 가 임베딩을 수십 번 연속 호출하는데, 거기서 503 한 번에
     * 카테고리 캐시 전체가 날아가던 위험을 없앤다.
     */
    private Map<?, ?> postWithRetry(String url, Object body, String label) {
        for (int attempt = 1; ; attempt++) {
            try {
                return restTemplate.postForObject(url, body, Map.class);
            } catch (HttpServerErrorException
                     | HttpClientErrorException.TooManyRequests
                     | ResourceAccessException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.error("[Gemini] {} — {}회 시도 모두 실패 ({})", label, attempt, causeOf(e));
                    throw e;
                }
                long waitMs = BACKOFF_BASE_MS * attempt;
                log.warn("[Gemini] {} 일시 장애({}) — {}ms 후 재시도 {}/{}",
                        label, causeOf(e), waitMs, attempt + 1, MAX_ATTEMPTS);
                sleep(waitMs);
            }
        }
    }

    /** 로그용 사유 — HTTP 응답이 있으면 상태코드, 타임아웃·연결 실패면 "timeout/IO". */
    private static String causeOf(RestClientException e) {
        return (e instanceof RestClientResponseException r)
                ? String.valueOf(r.getStatusCode().value())
                : "timeout/IO";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI 호출 대기 중 중단되었습니다.", ie);
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
