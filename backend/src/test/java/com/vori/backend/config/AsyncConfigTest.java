package com.vori.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 비동기 대기열이 꽉 찼을 때 지출 저장 요청이 오류로 끝나지 않는지.
 *
 * AiInquiryService.handleAnomalyEvent 는 @TransactionalEventListener(AFTER_COMMIT) + @Async("aiExecutor") 라
 * 지출 저장이 커밋된 직후, 같은 요청 스레드에서 aiExecutor 에 작업을 넘긴다. 여기서는 그 흐름을
 * 「커밋 뒤 콜백에서 executor.execute」로 그대로 재현한다(스프링 컨텍스트·DB 없이).
 */
class AsyncConfigTest {

    /** DB 없이 커밋 뒤 콜백만 도는 트랜잭션 관리자. */
    static class NoopTxManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition def) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }

    /** 「지출 저장」 트랜잭션 — 커밋 뒤 AI 작업을 executor 에 넘긴다. 저장(본문)이 끝났는지 돌려준다. */
    private static AtomicBoolean saveExpenseThenHandOff(Executor executor) {
        AtomicBoolean saved = new AtomicBoolean(false);
        new TransactionTemplate(new NoopTxManager()).executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(() -> {});
                }
            });
            saved.set(true);
        });
        return saved;
    }

    /** 스레드 5개를 붙잡고 대기열 100칸을 채운다 — 다음 작업부터 거절된다. */
    private static void fill(ThreadPoolTaskExecutor executor, CountDownLatch release) {
        for (int i = 0; i < 5 + 100; i++) {
            executor.execute(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    private static ThreadPoolTaskExecutor defaultPolicyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Test
    void 기본_거절_정책이면_커밋은_됐는데_요청이_예외로_끝난다() {
        ThreadPoolTaskExecutor executor = defaultPolicyExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            fill(executor, release);
            // 고치기 전 aiExecutor 와 같은 설정 — 커밋 뒤 콜백의 거절 예외가 저장 요청으로 올라온다
            assertThrows(RuntimeException.class, () -> saveExpenseThenHandOff(executor));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aiExecutor_는_꽉_차도_저장_요청을_실패시키지_않고_경고만_남긴다(CapturedOutput output) {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().aiExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            fill(executor, release);
            AtomicBoolean saved = assertDoesNotThrow(() -> saveExpenseThenHandOff(executor));
            assertTrue(saved.get());
            assertTrue(output.getAll().contains("AI 비동기 대기열이 꽉 차 작업을 건너뜀"));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
