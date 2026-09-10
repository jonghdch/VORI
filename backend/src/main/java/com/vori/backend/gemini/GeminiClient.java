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
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final RestTemplate restTemplate;
    /** 이미지 OCR 전용(읽기 60초). 필드명이 곧 빈 이름 — AsyncConfig 참조. */
    private final RestTemplate geminiImageRestTemplate;

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

    /**
     * 과지출(RED)에 이유를 묻는 질문.
     *
     * <p>이 질문은 <b>판정 직전</b>에 나간다. 여기서 이유를 짐작해 버리면 그 뒤의 판정이 가짜가 된다.
     * 예전 프롬프트가 "짧고 <b>따뜻한</b> 질문" 을 시켰더니 모델이 시킨 대로 따뜻하게 써서,
     * 충동구매에 대고 <i>"어떤 특별하고 마음 설레는 지출이었는지"</i> 라고 물었다.
     * 묻기도 전에 좋은 지출로 단정한 것이다.
     *
     * <p>반대로 차갑게 만들 수도 없다. 이 질문은 RED 전체에 나가므로 <b>병원비·장례비에도 나간다.</b>
     * 들뜨거나 나무라는 말투를 그래서 금지한다. 캐릭터성은 여기가 아니라
     * {@link #generateDailyComment}(펫이 말 거는 자리)에 둔다.
     *
     * <p>금액·평균을 숫자로 옮겨 적지 말라고 못박은 이유는, 값을 주면 모델이 그대로 낭독해
     * <i>"평소 평균 금액인 50,000원과 다르게 800,000원이 지출되었는데"</i> 같은 보고서 문장이 나오기 때문이다.
     *
     * <p>말투는 규칙보다 예시가 잘 먹혀 few-shot 을 둔다. 예시 항목(미용실·주유비)은 자주 쓰는
     * 카테고리와 겹치지 않게 골랐다 — 예시와 같은 항목이 들어오면 그대로 베껴 쓴다.
     *
     * <p>2026-09-10 실측: 병원비 50만 · 명품지갑 80만 · 축의금 20만 세 경우 모두
     * 긍정 전제·나무람·감탄사·보고서체·숫자 낭독 없이 나왔다.
     */
    public String generateQuestion(String item, int amount, BigDecimal meanEma, StatType statType) {
        String prompt = String.format("""
                너는 사용자의 지출 기록을 지켜보는 반려 펫이야. 사용자에게 말을 걸듯이 물어봐.

                사용자가 '%s' 항목에 %,d원을 썼어. 이 카테고리 평소 평균은 %,.0f원이야.
                이 숫자들은 네가 상황을 알기 위한 것이지, 문장에 옮겨 적으라는 게 아니야.

                이 지출을 왜 했는지 묻는 질문을 한국어 1문장으로 써.

                규칙:
                1. 좋은 지출로도 나쁜 지출로도 미리 단정하지 마.
                   '특별한', '소중한', '설레는' 처럼 긍정을 전제하는 말을 쓰지 마.
                2. 나무라거나 추궁하지 마. '왜 이렇게 많이', '괜찮겠어?' 같은 말도 쓰지 마.
                3. 금액이나 평균을 숫자로 적지 마. '평소보다 컸네' 처럼 말해.
                4. '지출되었는데' 같은 수동태나 보고서 말투를 쓰지 마. 말을 거는 능동태로 써.
                5. '오!', '앗!', '우와' 같은 감탄사를 쓰지 마. 병원비나 장례비일 수도 있어.
                   차분하되 다정하게.

                이런 결로 써:
                "이번 미용실은 평소보다 지출이 컸네. 어떤 일이었는지 가볍게 메모해 줄래?"
                "이번 주유비는 평소보다 많이 나왔네. 혹시 무슨 일이 있었는지 적어줄 수 있어?"

                질문만 출력해.""",
                item, amount, meanEma
        );
        return callGemini(prompt);
    }

    /**
     * 하루 소비 요약 → 펫이 사용자에게 말 거는 코멘트.
     * 통계 나열이 아니라 캐릭터의 말투로 뽑는 게 핵심 (VORI 의 스토리텔링 컨셉).
     *
     * <p><b>캐릭터성은 여기에만 둔다.</b> {@link #generateQuestion} 은 판정 직전이라 중립이어야 하고,
     * 발랄함은 전부 이 자리로 모은다. 듀오링고가 알림에서는 밀어붙이면서 채점은 정답/오답만
     * 내놓는 것과 같은 분리다.
     *
     * <p><b>절약액이 음수인 날을 실패로 전제하면 안 된다.</b> 이 메서드는 금액 합계만 받고
     * <b>사유를 모른다</b> — 병원비일 수도 장례비일 수도 있다. 거기에 대고 "내일은 더 잘할 수
     * 있을 거야" 라고 하면 잘못했다고 단정하는 말이 된다. 짐작해서 위로하는 것도 같은 이유로 막았다.
     * VORI 가 과지출에 감점 대신 무보상을 쓰는 것(`max(saved,0)`)과 같은 철학이다.
     *
     * <p>이모지를 양수/음수로 나눠 개수를 다르게 주지 않는다. 조건이 늘면 모델이 헷갈려
     * 음수인 날에 축포가 터질 수 있다. 최대 1개로 묶는 편이 안전하다.
     *
     * <p>호칭을 규칙으로 고정한 이유는, 안 박아두면 한 문장에서는 "주인" 을 쓰고 다른 문장에서는
     * "너" 를 써서 같은 펫이 사용자를 다르게 부르기 때문이다. "주인" 은 상하관계가 있는 말이라
     * 친구 같은 캐릭터와도 맞지 않는다.
     *
     * <p>2026-09-10 실측:
     * <ul>
     *   <li>절약 +38,000 → "어제 네가 진짜 많이 아껴준 덕분에 내 힘이 쑥쑥 올랐어!…"</li>
     *   <li>절약 −480,000 → "어제는 평소보다 챙겨야 할 일이 많았던 하루였나 봐.
     *       나는 오늘도 여기서 너 기다리면서 꼬리 흔들고 있을게"</li>
     * </ul>
     * 음수인 날의 "챙겨야 할 일이 많았던" 은 지시하지 않은 표현이다 — 사유를 모른다는 제약을
     * 모델이 문장으로 옮긴 것이라, 이 제약이 실제로 전달되고 있다는 신호로 본다.
     *
     * <p>한계: 어제 무엇을 샀는지도, AI 가 인정한 지출이 있었는지도 모른다. 그 맥락을 넘겨주면
     * 코멘트가 훨씬 구체적이어지지만 시그니처·호출부·스케줄러를 함께 고쳐야 해서 미뤄 뒀다.
     *
     * @param petName 말하는 주체가 될 펫 종족명. 펫이 없으면 null.
     */
    public String generateDailyComment(String petName, int expenseTotal, int incomeTotal,
                                       int savedAmount, int statDelta) {
        String intro = (petName == null || petName.isBlank())
                ? "너는 사용자의 절약을 돕는 반려 펫이야."
                : "너는 사용자가 키우는 반려 펫 '" + petName + "'야.";

        String prompt = String.format("""
                %s 어제 하루를 지켜봤고, 지금 사용자에게 말을 건다.

                어제 요약:
                - 지출 합계: %,d원
                - 수입 합계: %,d원
                - 평소 소비 패턴 대비 절약액: %,d원 (음수면 평소보다 더 썼다는 뜻)
                - 그래서 오른 내 스탯: %d

                한국어 2문장으로 코멘트를 써.

                규칙:
                1. 숫자를 그대로 나열하지 마. 이야기하듯 말해.
                2. 절약액이 양수면 마음껏 기뻐하고 자랑해. 스탯이 올랐으면 그것도 같이 신나 해.
                3. 절약액이 음수여도 잘못했다고 전제하지 마. 너는 이유를 모른다.
                   병원에 갔을 수도, 장례식에 다녀왔을 수도 있어.
                   - '내일은 더 잘할 수 있을 거야', '조금만 아껴보자' 처럼 반성을 요구하는 말을 쓰지 마.
                   - 무슨 일이 있었는지 모르니 짐작해서 위로하지도 마.
                   - 그냥 곁에 있는 말을 해.
                4. 가르치려 들지 마. 절약 조언이나 훈수 금지.
                5. 이모지는 최대 1개.
                6. 사용자는 '너', '네' 로 불러. 친구처럼 편하게. '주인' 이라는 말은 쓰지 마.

                절약액이 음수인 날은 이런 결로:
                "어제는 평소보다 나갈 데가 있었나 보네. 나는 오늘도 여기서 기다리고 있을게 🌱"

                코멘트만 출력해.""",
                intro, expenseTotal, incomeTotal, savedAmount, statDelta
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

    /**
     * 영수증 이미지 → 가계부 등록용 구조화 JSON 문자열.
     *
     * responseMimeType 을 application/json 으로 지정해 마크다운 코드블록 없이 순수 JSON 만 받는다.
     * 텍스트 추출 후 별도로 항목을 골라내는 단계(KIE)가 필요 없다 — 모델이 바로 구조를 만들어 준다.
     *
     * 반환값은 파싱하지 않은 원문이다. 호출부가 DTO 로 매핑하고, 원문은 그대로 저장해
     * 나중에 값을 다시 확인할 수 있게 한다.
     */
    public String extractReceipt(byte[] image, String mimeType) {
        String prompt = """
                이 영수증 이미지에서 가계부 등록에 필요한 정보를 뽑아 JSON 으로만 출력하세요.

                {
                  "storeName": "상호명",
                  "date": "YYYY-MM-DD",
                  "time": "HH:MM",
                  "totalAmount": 총결제금액(정수, 원),
                  "items": [{"name": "품목명", "quantity": 수량(정수), "amount": 금액(정수)}],
                  "paymentMethod": "CASH|CREDIT|DEBIT|TRANSFER|MOBILE_PAY|UNKNOWN",
                  "representativeItem": "대표 품목 한 개(가장 비싸거나 대표적인 것)"
                }

                주의:
                - 금액은 콤마 없이 정수로만. 읽을 수 없는 값은 null.
                - 영수증이 아니거나 판독 불가능하면 모든 값을 null 로 두세요.
                """;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", prompt),
                        Map.of("inline_data", Map.of(
                                "mime_type", mimeType,
                                "data", Base64.getEncoder().encodeToString(image)))))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        try {
            return extractText(postWithRetry(
                    BASE_URL + apiKey, body, "extractReceipt", geminiImageRestTemplate));
        } catch (Exception e) {
            log.error("Gemini 영수증 인식 실패", e);
            throw new RuntimeException("영수증 인식에 실패했습니다.");
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
        return postWithRetry(url, body, label, restTemplate);
    }

    private Map<?, ?> postWithRetry(String url, Object body, String label, RestTemplate client) {
        for (int attempt = 1; ; attempt++) {
            try {
                return client.postForObject(url, body, Map.class);
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
