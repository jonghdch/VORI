package com.vori.backend.gemini;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.vori.backend.inquiry.ReasonCategory;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gemini 재시도·대체 모델 정책, 일일 코멘트 프롬프트 구성 검증.
 * 실제로 503 이 뜰 때만 드러나는 로직이라 목으로 고정해서 확인한다.
 */
class GeminiClientTest {

    /** extractText 가 파싱할 수 있는 정상 응답 형태. */
    private static final Map<String, Object> OK_RESPONSE = Map.of(
            "candidates", List.of(Map.of(
                    "content", Map.of("parts", List.of(Map.of("text", "왜 이렇게 쓰셨나요?"))))));

    /** 일일 코멘트 입력 — 이름은 아직 없고 수입은 0 인 펫. */
    private static GeminiClient.DailyCommentInput comment(String species, int expense, int saved, int stat,
                                                          List<ReasonCategory> reasons) {
        return new GeminiClient.DailyCommentInput(null, species, expense, 0, saved, stat, reasons);
    }

    /** 대체 모델 없음 — 기존 재시도 테스트는 이 조건에서 돈다. */
    private static GeminiClient client(RestTemplate rt) {
        return client(rt, "");
    }

    /**
     * 텍스트용·이미지용 두 RestTemplate 을 같은 목으로 채운다 — 재시도 정책은 공유된다.
     * 모델 설정은 스프링이 넣어 주는 값이라 테스트에서는 직접 채운다.
     */
    private static GeminiClient client(RestTemplate rt, String fallbackModels) {
        GeminiClient c = new GeminiClient(rt, rt);
        ReflectionTestUtils.setField(c, "model", "primary");
        ReflectionTestUtils.setField(c, "fallbackModels", fallbackModels);
        ReflectionTestUtils.setField(c, "embeddingModel", "embedder");
        return c;
    }

    private static String primaryUrl() {
        return contains("/models/primary:");
    }

    private static String backupUrl() {
        return contains("/models/backup:");
    }

    private static HttpServerErrorException serverError(HttpStatus status) {
        return (HttpServerErrorException) HttpServerErrorException.create(
                status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0], null);
    }

    private static HttpClientErrorException clientError(HttpStatus status) {
        return (HttpClientErrorException) HttpClientErrorException.create(
                status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0], null);
    }

    @Test
    @DisplayName("503 이 두 번 떠도 세 번째 시도가 성공하면 결과를 돌려준다")
    void retriesOnServiceUnavailableThenSucceeds() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(serverError(HttpStatus.SERVICE_UNAVAILABLE))
                .thenThrow(serverError(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(OK_RESPONSE);

        String result = client(rt)
                .generateQuestion("무신사 자켓", 89_000, BigDecimal.valueOf(20_000), null);

        assertThat(result).isEqualTo("왜 이렇게 쓰셨나요?");
        verify(rt, times(3)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("429 도 일시적 장애로 보고 재시도한다")
    void retriesOnTooManyRequests() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(clientError(HttpStatus.TOO_MANY_REQUESTS))
                .thenReturn(OK_RESPONSE);

        String result = client(rt)
                .generateQuestion("커피", 8_000, BigDecimal.valueOf(4_000), null);

        assertThat(result).isNotBlank();
        verify(rt, times(2)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("읽기 타임아웃도 재시도한다 — HTTP 상태가 없어 놓치기 쉬운 경로")
    void retriesOnReadTimeout() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Request timed out"))
                .thenReturn(OK_RESPONSE);

        String result = client(rt)
                .generateDailyComment(comment("강아지", 303_000, -3_000, 57, List.of()));

        assertThat(result).isNotBlank();
        verify(rt, times(2)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("404(모델 은퇴)는 재시도 없이 즉시 실패한다")
    void doesNotRetryOnNotFound() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(clientError(HttpStatus.NOT_FOUND));

        GeminiClient client = client(rt);

        assertThatThrownBy(() -> client.generateQuestion("책", 15_000, BigDecimal.valueOf(9_000), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI 서비스 호출에 실패");

        // 핵심: 복구 불가능한 에러에 3배 시간을 쓰지 않는다
        verify(rt, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("403(권한)도 재시도 없이 즉시 실패한다")
    void doesNotRetryOnForbidden() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(clientError(HttpStatus.FORBIDDEN));

        GeminiClient client = client(rt);

        assertThatThrownBy(() -> client.embed("스타벅스 아메리카노"))
                .isInstanceOf(RuntimeException.class);

        verify(rt, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("503 이 계속되면 3회까지만 시도하고 포기한다")
    void givesUpAfterMaxAttempts() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(serverError(HttpStatus.SERVICE_UNAVAILABLE));

        GeminiClient client = client(rt);

        assertThatThrownBy(() -> client.generateQuestion("옷", 50_000, BigDecimal.valueOf(20_000), null))
                .isInstanceOf(RuntimeException.class);

        verify(rt, times(3)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("영수증 인식도 같은 재시도 정책을 탄다")
    void receiptSharesRetryPolicy() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Request timed out"))
                .thenReturn(Map.of("candidates", List.of(Map.of(
                        "content", Map.of("parts", List.of(
                                Map.of("text", "{\"storeName\":\"GS25\",\"totalAmount\":8000}")))))));

        String json = client(rt).extractReceipt(new byte[]{1, 2, 3}, "image/png");

        assertThat(json).contains("GS25");
        verify(rt, times(2)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("임베딩 호출도 같은 재시도 정책을 탄다")
    void embedSharesRetryPolicy() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(serverError(HttpStatus.BAD_GATEWAY))
                .thenReturn(Map.of("embedding", Map.of("values", List.of(0.1, 0.2, 0.3))));

        double[] vec = client(rt).embed("아메리카노");

        assertThat(vec).containsExactly(0.1, 0.2, 0.3);
        verify(rt, times(2)).postForObject(anyString(), any(), eq(Map.class));
    }

    // ───── 대체 모델 ─────

    @Test
    @DisplayName("주 모델이 503 을 3번 내면 대체 모델로 넘어간다")
    void fallsBackAfterServerErrors() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(primaryUrl(), any(), eq(Map.class)))
                .thenThrow(serverError(HttpStatus.SERVICE_UNAVAILABLE));
        when(rt.postForObject(backupUrl(), any(), eq(Map.class)))
                .thenReturn(OK_RESPONSE);

        String result = client(rt, "backup")
                .generateQuestion("축의금", 200_000, BigDecimal.valueOf(40_000), null);

        assertThat(result).isEqualTo("왜 이렇게 쓰셨나요?");
        verify(rt, times(3)).postForObject(primaryUrl(), any(), eq(Map.class));
        verify(rt, times(1)).postForObject(backupUrl(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("429(한도 초과)는 같은 모델에 재시도하지 않고 바로 대체 모델로 — 한도는 모델별")
    void fallsBackImmediatelyOnTooManyRequests() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(primaryUrl(), any(), eq(Map.class)))
                .thenThrow(clientError(HttpStatus.TOO_MANY_REQUESTS));
        when(rt.postForObject(backupUrl(), any(), eq(Map.class)))
                .thenReturn(OK_RESPONSE);

        String result = client(rt, "backup")
                .generateQuestion("커피", 8_000, BigDecimal.valueOf(4_000), null);

        assertThat(result).isNotBlank();
        verify(rt, times(1)).postForObject(primaryUrl(), any(), eq(Map.class));
        verify(rt, times(1)).postForObject(backupUrl(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("404(모델 은퇴)면 대체 모델로 넘어간다")
    void fallsBackOnNotFound() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(primaryUrl(), any(), eq(Map.class)))
                .thenThrow(clientError(HttpStatus.NOT_FOUND));
        when(rt.postForObject(backupUrl(), any(), eq(Map.class)))
                .thenReturn(OK_RESPONSE);

        String result = client(rt, "backup")
                .generateDailyComment(comment("강아지", 10_000, 5_000, 5, List.of()));

        assertThat(result).isNotBlank();
        verify(rt, times(1)).postForObject(primaryUrl(), any(), eq(Map.class));
        verify(rt, times(1)).postForObject(backupUrl(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("403(키 문제)은 대체 모델로 넘기지 않는다 — 어느 모델이든 같은 키라 같은 답")
    void doesNotFallBackOnForbidden() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(primaryUrl(), any(), eq(Map.class)))
                .thenThrow(clientError(HttpStatus.FORBIDDEN));

        GeminiClient client = client(rt, "backup");

        assertThatThrownBy(() -> client.generateQuestion("책", 15_000, BigDecimal.valueOf(9_000), null))
                .isInstanceOf(RuntimeException.class);
        verify(rt, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("타임아웃은 대체 모델로 넘기지 않는다 — 이미 오래 기다렸다")
    void doesNotFallBackOnTimeout() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(primaryUrl(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Request timed out"));

        GeminiClient client = client(rt, "backup");

        assertThatThrownBy(() -> client.extractReceipt(new byte[]{1, 2, 3}, "image/png"))
                .isInstanceOf(RuntimeException.class);
        verify(rt, times(3)).postForObject(primaryUrl(), any(), eq(Map.class));
        verify(rt, never()).postForObject(backupUrl(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("영수증 인식도 대체 모델로 넘어간다")
    void receiptFallsBack() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(primaryUrl(), any(), eq(Map.class)))
                .thenThrow(clientError(HttpStatus.TOO_MANY_REQUESTS));
        when(rt.postForObject(backupUrl(), any(), eq(Map.class)))
                .thenReturn(Map.of("candidates", List.of(Map.of(
                        "content", Map.of("parts", List.of(
                                Map.of("text", "{\"storeName\":\"GS25\",\"totalAmount\":8000}")))))));

        String json = client(rt, "backup").extractReceipt(new byte[]{1, 2, 3}, "image/png");

        assertThat(json).contains("GS25");
    }

    @Test
    @DisplayName("대체 모델까지 모두 실패하면 포기한다")
    void givesUpWhenAllModelsFail() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(serverError(HttpStatus.SERVICE_UNAVAILABLE));

        GeminiClient client = client(rt, "backup");

        assertThatThrownBy(() -> client.generateQuestion("옷", 50_000, BigDecimal.valueOf(20_000), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI 서비스 호출에 실패");
        verify(rt, times(3)).postForObject(primaryUrl(), any(), eq(Map.class));
        verify(rt, times(3)).postForObject(backupUrl(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("임베딩은 대체 모델을 쓰지 않는다 — 모델마다 벡터 공간이 다르다")
    void embedNeverFallsBack() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(clientError(HttpStatus.TOO_MANY_REQUESTS));

        GeminiClient client = client(rt, "backup");

        assertThatThrownBy(() -> client.embed("아메리카노")).isInstanceOf(RuntimeException.class);
        verify(rt, times(3)).postForObject(contains("/models/embedder:embedContent"), any(), eq(Map.class));
        verify(rt, never()).postForObject(backupUrl(), any(), eq(Map.class));
    }

    // ───── 하루 한도 ─────

    private static final String PER_DAY = "GenerateRequestsPerDayPerProjectPerModel-FreeTier";
    private static final String PER_MINUTE = "GenerateRequestsPerMinutePerProjectPerModel-FreeTier";

    /** 2026-11-12 10:00 KST — 경진대회 날 오전. 태평양은 11/11 17:00(PST)이라 한도는 한국 17시에 풀린다. */
    private static final Instant DEMO_MORNING = Instant.parse("2026-11-12T01:00:00Z");

    /** 실제 429 본문처럼 quotaId 를 실은 응답. */
    private static HttpClientErrorException quota(String quotaId) {
        String body = "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"details\":[{\"violations\":"
                + "[{\"quotaId\":\"" + quotaId + "\"}]}]}}";
        return (HttpClientErrorException) HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests", HttpHeaders.EMPTY, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private static GeminiClient at(GeminiClient c, Instant now) {
        ReflectionTestUtils.setField(c, "clock", Clock.fixed(now, ZoneOffset.UTC));
        return c;
    }

    @Test
    @DisplayName("하루 한도를 다 쓴 주 모델은 리셋까지 건너뛰고 처음부터 대체 모델로 보낸다")
    void skipsExhaustedPrimaryUntilReset() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(primaryUrl(), any(), eq(Map.class))).thenThrow(quota(PER_DAY));
        when(rt.postForObject(backupUrl(), any(), eq(Map.class))).thenReturn(OK_RESPONSE);
        GeminiClient client = at(client(rt, "backup"), DEMO_MORNING);

        client.generateQuestion("커피", 8_000, BigDecimal.valueOf(4_000), null);
        client.generateQuestion("커피", 8_000, BigDecimal.valueOf(4_000), null);

        verify(rt, times(1)).postForObject(primaryUrl(), any(), eq(Map.class));
        verify(rt, times(2)).postForObject(backupUrl(), any(), eq(Map.class));
        assertThat(client.quotaStatus()).containsExactly(
                new GeminiClient.ModelQuota("primary", true, "오후 5시"),
                new GeminiClient.ModelQuota("backup", false, null));
    }

    @Test
    @DisplayName("모든 모델이 하루 한도를 다 쓰면 언제 되는지 실어 던지고, 리셋 전엔 Gemini 를 부르지 않는다")
    void allModelsExhaustedFailFastUntilReset() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(primaryUrl(), any(), eq(Map.class)))
                .thenThrow(quota(PER_DAY))
                .thenReturn(OK_RESPONSE);
        when(rt.postForObject(backupUrl(), any(), eq(Map.class))).thenThrow(quota(PER_DAY));
        GeminiClient client = at(client(rt, "backup"), DEMO_MORNING);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.chat("너는 펫이야", List.of(new GeminiClient.ChatTurn(true, "안녕"))))
                    .isInstanceOfSatisfying(AiQuotaException.class, e -> {
                        assertThat(e.isDaily()).isTrue();
                        assertThat(e.getAvailableAt()).isEqualTo("오후 5시");
                    });
        }
        // 두 번째는 HTTP 없이 바로 — 하루 한도는 몇 번을 다시 보내도 안 풀린다
        verify(rt, times(1)).postForObject(primaryUrl(), any(), eq(Map.class));
        verify(rt, times(1)).postForObject(backupUrl(), any(), eq(Map.class));

        // 태평양 자정(한국 17시)이 지나면 다시 부른다
        at(client, Instant.parse("2026-11-12T08:00:00Z"));
        assertThat(client.chat("너는 펫이야", List.of(new GeminiClient.ChatTurn(true, "안녕")))).isNotBlank();
    }

    @Test
    @DisplayName("분당 한도는 재시도하고, 그래도 막히면 「잠깐 몰림」으로 던지고 기억하지 않는다")
    void perMinuteQuotaIsNotRemembered() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenThrow(quota(PER_MINUTE));
        GeminiClient client = at(client(rt), DEMO_MORNING);

        assertThatThrownBy(() -> client.extractReceipt(new byte[]{1, 2, 3}, "image/png"))
                .isInstanceOfSatisfying(AiQuotaException.class, e -> assertThat(e.isDaily()).isFalse());
        verify(rt, times(3)).postForObject(anyString(), any(), eq(Map.class));
        assertThat(client.availableModels()).containsExactly("primary");
    }

    @Test
    @DisplayName("대체 모델이 없어도 하루 한도면 같은 모델에 재시도하지 않는다")
    void dailyQuotaIsNotRetried() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenThrow(quota(PER_DAY));
        GeminiClient client = at(client(rt), DEMO_MORNING);

        assertThatThrownBy(() -> client.generateQuestion("책", 15_000, BigDecimal.valueOf(9_000), null))
                .isInstanceOf(AiQuotaException.class);
        verify(rt, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("리셋 직전에 보낸 요청의 하루 한도 429 를 리셋 뒤에 받으면 기억하지 않는다 — 다음 날까지 막히지 않게")
    void dailyQuotaAnsweredAfterResetIsNotRemembered() {
        RestTemplate rt = mock(RestTemplate.class);
        GeminiClient client = at(client(rt), Instant.parse("2026-11-12T07:59:58Z")); // 16:59:58 KST 에 보냄
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenAnswer(inv -> {
            at(client, Instant.parse("2026-11-12T08:00:05Z")); // 응답은 17:00:05 — 이미 한도가 풀린 뒤
            throw quota(PER_DAY);
        });

        assertThatThrownBy(() -> client.generateQuestion("책", 15_000, BigDecimal.valueOf(9_000), null))
                .isInstanceOfSatisfying(AiQuotaException.class, e -> assertThat(e.isDaily()).isFalse());
        assertThat(client.availableModels()).containsExactly("primary");
    }

    @Test
    @DisplayName("다시 쓸 수 있는 때는 한국 시각으로 — 서머타임에 따라 16시·17시, 날이 넘어가면 「내일」")
    void availableAtInKoreanTime() {
        GeminiClient client = client(mock(RestTemplate.class), "backup");

        at(client, DEMO_MORNING);
        assertThat(client.availableAtLabel(client.nextQuotaReset())).isEqualTo("오후 5시");

        at(client, Instant.parse("2026-10-07T01:00:00Z")); // 10/7 10:00 KST, 태평양 서머타임
        assertThat(client.availableAtLabel(client.nextQuotaReset())).isEqualTo("오후 4시");

        at(client, Instant.parse("2026-11-12T09:30:00Z")); // 18:30 KST — 오늘 한도는 이미 17시에 풀렸다
        assertThat(client.availableAtLabel(client.nextQuotaReset())).isEqualTo("내일 오후 5시");
    }

    @Test
    @DisplayName("대체 모델 설정의 빈 칸·중복·주 모델은 걸러 낸다")
    void modelChainSkipsBlanksAndDuplicates() {
        GeminiClient client = client(mock(RestTemplate.class), " backup, ,primary,backup ");

        assertThat(client.modelChain()).containsExactly("primary", "backup");
    }

    // ───── 일일 코멘트 프롬프트 ─────

    /** 실제로 Gemini 에 보낸 프롬프트 문장을 꺼낸다. */
    @SuppressWarnings("unchecked")
    private static String sentPrompt(RestTemplate rt) {
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(rt).postForObject(anyString(), body.capture(), eq(Map.class));
        Map<String, Object> b = (Map<String, Object>) body.getValue();
        Map<String, Object> content = ((List<Map<String, Object>>) b.get("contents")).get(0);
        return (String) ((List<Map<String, Object>>) content.get("parts")).get(0).get("text");
    }

    @Test
    @DisplayName("일일 코멘트에 그날 답한 지출 이유가 판단 없는 말로 들어간다")
    void dailyCommentIncludesReasons() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(OK_RESPONSE);

        client(rt).generateDailyComment(new GeminiClient.DailyCommentInput(
                "콩이", "강아지", 230_000, 0, -160_000, 44,
                List.of(ReasonCategory.CEREMONY, ReasonCategory.IMPULSE, ReasonCategory.IMPULSE)));

        String prompt = sentPrompt(rt);
        assertThat(prompt).contains("강아지 '콩이'");
        assertThat(prompt).contains("경조사(결혼식·장례식 등) 1건");
        assertThat(prompt).contains("사고 싶어서 산 것 2건");
        // 분류 이름(IMPULSE)을 "충동" 같은 판단이 섞인 말로 옮기지 않는다
        assertThat(prompt).doesNotContain("충동").doesNotContain("IMPULSE");
    }

    @Test
    @DisplayName("답한 이유가 없으면 '없음' — 예전처럼 금액만 보고 말한다")
    void dailyCommentWithoutReasons() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(OK_RESPONSE);

        client(rt).generateDailyComment(comment("고양이", 12_000, 3_000, 0, List.of()));

        assertThat(sentPrompt(rt)).contains("사용자가 직접 밝힌 지출 이유: 없음");
    }

    @Test
    @DisplayName("펫 이름이 있으면 '종족 이름' 으로, 없으면 종족명으로, 펫이 없으면 일반 펫으로 말한다")
    void introUsesPetName() {
        assertThat(GeminiClient.introFor("콩이", "강아지")).isEqualTo("너는 사용자가 키우는 강아지 '콩이'야.");
        assertThat(GeminiClient.introFor(null, "강아지")).isEqualTo("너는 사용자가 키우는 반려 펫 '강아지'야.");
        assertThat(GeminiClient.introFor(" ", null)).isEqualTo("너는 사용자의 절약을 돕는 반려 펫이야.");
    }

    @Test
    @DisplayName("펫 대화는 설정을 systemInstruction 으로, 지난 대화를 user/model 역할로 보낸다")
    @SuppressWarnings("unchecked")
    void chatSendsSystemInstructionAndRoles() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class))).thenReturn(OK_RESPONSE);

        client(rt).chat("너는 펫이야", List.of(
                new GeminiClient.ChatTurn(true, "안녕"),
                new GeminiClient.ChatTurn(false, "반가워요!"),
                new GeminiClient.ChatTurn(true, "뭐 해?")));

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(rt).postForObject(primaryUrl(), body.capture(), eq(Map.class));
        Map<String, Object> sent = (Map<String, Object>) body.getValue();
        assertThat(sent.get("systemInstruction").toString()).contains("너는 펫이야");
        List<Map<String, Object>> contents = (List<Map<String, Object>>) sent.get("contents");
        assertThat(contents).extracting(c -> c.get("role")).containsExactly("user", "model", "user");
        assertThat(contents.toString()).doesNotContain("너는 펫이야");
    }
}
