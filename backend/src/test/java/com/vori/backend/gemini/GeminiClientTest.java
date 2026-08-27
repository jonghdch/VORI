package com.vori.backend.gemini;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gemini 재시도 정책 검증.
 * 실제로 503 이 뜰 때만 드러나는 로직이라 목으로 고정해서 확인한다.
 */
class GeminiClientTest {

    /** extractText 가 파싱할 수 있는 정상 응답 형태. */
    private static final Map<String, Object> OK_RESPONSE = Map.of(
            "candidates", List.of(Map.of(
                    "content", Map.of("parts", List.of(Map.of("text", "왜 이렇게 쓰셨나요?"))))));

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

        String result = new GeminiClient(rt)
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

        String result = new GeminiClient(rt)
                .generateQuestion("커피", 8_000, BigDecimal.valueOf(4_000), null);

        assertThat(result).isNotBlank();
        verify(rt, times(2)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("404(모델 은퇴)는 재시도 없이 즉시 실패한다")
    void doesNotRetryOnNotFound() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(clientError(HttpStatus.NOT_FOUND));

        GeminiClient client = new GeminiClient(rt);

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

        GeminiClient client = new GeminiClient(rt);

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

        GeminiClient client = new GeminiClient(rt);

        assertThatThrownBy(() -> client.generateQuestion("옷", 50_000, BigDecimal.valueOf(20_000), null))
                .isInstanceOf(RuntimeException.class);

        verify(rt, times(3)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("임베딩 호출도 같은 재시도 정책을 탄다")
    void embedSharesRetryPolicy() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(serverError(HttpStatus.BAD_GATEWAY))
                .thenReturn(Map.of("embedding", Map.of("values", List.of(0.1, 0.2, 0.3))));

        double[] vec = new GeminiClient(rt).embed("아메리카노");

        assertThat(vec).containsExactly(0.1, 0.2, 0.3);
        verify(rt, times(2)).postForObject(anyString(), any(), eq(Map.class));
    }
}
