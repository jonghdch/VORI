package com.vori.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling // 일일 리포트 배치(DailyReportScheduler)용
public class AsyncConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3)) // 3초 내에 서버와 연결이 안 되면 포기
                // Gemini 는 성공하는 호출도 7~14초가 걸린다(gemini-3.6-flash 실측).
                // 15초로 잡으면 정상 응답까지 타임아웃으로 버리게 되어 30초로 둔다.
                // 이 RestTemplate 은 GeminiClient 전용이라 다른 호출에 영향이 없다.
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 영수증 OCR 전용 — 이미지 처리는 텍스트보다 훨씬 오래 걸린다(실측 4.9~25.9초).
     * 위의 30초로는 정상 응답까지 타임아웃으로 잘라내므로 60초를 준다.
     *
     * 빈 이름과 주입받는 쪽 필드명을 맞춰두면 @Qualifier 없이 해결된다 —
     * Lombok 의 @RequiredArgsConstructor 는 필드의 @Qualifier 를 생성자로 옮기지 않아서,
     * 어노테이션에 기대면 주입이 모호해진다.
     */
    @Bean("geminiImageRestTemplate")
    public RestTemplate geminiImageRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * AI 문구 생성처럼 없어도 기능이 멈추지 않는 비동기 작업용 (AiInquiryService.handleAnomalyEvent).
     *
     * <p>꽉 차면(스레드 5개가 다 일하고 대기 100개) 작업을 버리고 경고만 남긴다. 기본 정책(AbortPolicy)은
     * 예외를 던지는데, 이 작업은 지출 저장이 커밋된 직후(AFTER_COMMIT) 같은 요청 스레드에서 넘겨져서
     * 그 예외가 저장 요청까지 올라간다 — 지출은 이미 저장됐는데 사용자는 오류를 보고 다시 눌러 같은 지출이
     * 두 번 들어간다. 버려도 질문은 지출과 같은 트랜잭션에서 템플릿 문구로 이미 저장돼 있다.
     * CallerRunsPolicy 는 Gemini 응답(7~14초)을 저장 요청이 기다리게 되어 쓰지 않는다.
     */
    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-async-");
        executor.setRejectedExecutionHandler(SKIP_WHEN_FULL);
        executor.initialize();
        return executor;
    }

    static final RejectedExecutionHandler SKIP_WHEN_FULL = (task, pool) ->
            log.warn("AI 비동기 대기열이 꽉 차 작업을 건너뜀 — 질문은 템플릿 문구로 남는다 (실행 중 {}, 대기 {})",
                    pool.getActiveCount(), pool.getQueue().size());
}