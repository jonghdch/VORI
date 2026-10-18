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

    /**
     * 업적·펫 칭호 검사용 (TitleService.onTitleCheck, PetTitleService.onPetTitleCheck).
     *
     * <p>두 검사는 커밋 직후(AFTER_COMMIT) 같은 요청 스레드에서 새 트랜잭션(REQUIRES_NEW)으로 돌았다. 그런데
     * 커밋 직후 단계에서도 원래 트랜잭션의 DB 연결은 아직 반납 전이라, 요청 하나가 연결 두 개를 동시에 쥐었다.
     * 같은 순간에 10명(Hikari 기본 연결 10개)이 로그인·지출 저장·판정을 하면 서로 두 번째 연결을 기다려 30초 동안
     * 서버 전체가 멈추고 500 이 났다(10/16 부하 시험 30명·100명, 스레드 덤프로 확인). 이 실행기로 넘기면 요청은
     * 넘기기만 하고 끝나 연결을 반납하고, 검사 스레드는 연결 하나만 쓴다.
     *
     * <p>꽉 차면 건너뛴다. 두 검사는 매번 모든 조건을 처음부터 다시 봐서, 건너뛴 지급은 다음 동작 때 채워진다.
     * 같은 요청 스레드에서 돌리는 CallerRunsPolicy 는 위의 교착을 되살려 쓰지 않는다.
     */
    @Bean("titleExecutor")
    public Executor titleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("title-async-");
        executor.setRejectedExecutionHandler(SKIP_TITLE_CHECK_WHEN_FULL);
        executor.initialize();
        return executor;
    }

    static final RejectedExecutionHandler SKIP_TITLE_CHECK_WHEN_FULL = (task, pool) ->
            log.warn("칭호 검사 대기열이 꽉 차 건너뜀 — 다음 동작 때 다시 본다 (실행 중 {}, 대기 {})",
                    pool.getActiveCount(), pool.getQueue().size());
}