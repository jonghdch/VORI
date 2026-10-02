package com.vori.backend.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vori.backend.gemini.GeminiClient;
import com.vori.backend.receipt.dto.ReceiptOcrResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 결제 캡처 인식 결과를 가계부 값으로 옮기는 규칙 검증. Gemini 는 목으로 고정한다.
 */
class ReceiptServiceTest {

    private final ReceiptOcrJobRepository repository = mock(ReceiptOcrJobRepository.class);
    private final GeminiClient geminiClient = mock(GeminiClient.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final ReceiptService service = new ReceiptService(
            repository, geminiClient, new ObjectMapper(), tx, mock(ApplicationEventPublisher.class));

    private final ReceiptOcrJob job = ReceiptOcrJob.builder()
            .userId(1L).provider(OcrProvider.GEMINI).status(OcrStatus.PROCESSING)
            .requestedAt(LocalDateTime.now()).build();

    private static final MockMultipartFile PNG =
            new MockMultipartFile("file", "capture.png", "image/png", new byte[]{1, 2, 3});

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 트랜잭션 콜백을 그대로 실행한다
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        when(repository.save(any())).thenReturn(job);
        when(repository.findById(any())).thenReturn(Optional.of(job));
    }

    @Test
    @DisplayName("카드 승인 문자 캡처는 금액·날짜·가맹점을 채운다")
    void cardAlertFillsValues() {
        when(geminiClient.extractReceipt(any(), anyString())).thenReturn("""
                {"sourceType":"CARD_ALERT","storeName":"스타벅스 강남점","date":"2026-10-01","time":"12:34",
                 "totalAmount":12500,"items":null,"paymentMethod":"CREDIT","representativeItem":null}""");

        ReceiptOcrResponse r = service.process(1L, PNG);

        assertThat(r.status()).isEqualTo(OcrStatus.SUCCESS);
        assertThat(r.amount()).isEqualTo(12_500);
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(r.item()).isEqualTo("스타벅스 강남점"); // 품목이 없으면 가맹점명
        assertThat(r.extracted().sourceType()).isEqualTo("CARD_ALERT");
    }

    @Test
    @DisplayName("여러 건이 찍힌 캡처는 모델이 하나를 골라 채워 와도 값을 비운다")
    void multipleIsNeverFilled() {
        when(geminiClient.extractReceipt(any(), anyString())).thenReturn("""
                {"sourceType":"MULTIPLE","storeName":"GS25","date":"2026-09-30","time":null,
                 "totalAmount":4500,"items":null,"paymentMethod":"DEBIT","representativeItem":null}""");

        ReceiptOcrResponse r = service.process(1L, PNG);

        assertThat(r.status()).isEqualTo(OcrStatus.SUCCESS);
        assertThat(r.amount()).isNull();
        assertThat(r.date()).isNull();
        assertThat(r.item()).isNull();
        assertThat(r.extracted().sourceType()).isEqualTo("MULTIPLE"); // 화면이 이유를 안내할 수 있게
    }

    @Test
    @DisplayName("sourceType 이 없는 예전 원문도 그대로 읽힌다")
    void legacyJsonWithoutSourceType() {
        when(geminiClient.extractReceipt(any(), anyString())).thenReturn("""
                {"storeName":"GS25","date":"2026-09-17","totalAmount":9900,"representativeItem":"도시락"}""");

        ReceiptOcrResponse r = service.process(1L, PNG);

        assertThat(r.amount()).isEqualTo(9_900);
        assertThat(r.item()).isEqualTo("도시락");
        assertThat(r.extracted().sourceType()).isNull();
    }
}
