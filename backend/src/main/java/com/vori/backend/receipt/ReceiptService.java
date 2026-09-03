package com.vori.backend.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vori.backend.gemini.GeminiClient;
import com.vori.backend.receipt.dto.ExtractedReceipt;
import com.vori.backend.receipt.dto.ReceiptOcrResponse;
import com.vori.backend.title.TitleCheckEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * 영수증 사진 → 가계부 입력값 추출.
 *
 * 이미지는 저장하지 않는다. 메모리에서 Gemini 로 넘기고 결과만 남긴다 —
 * 가계부에 필요한 건 추출된 데이터이고, 영수증에는 카드번호 뒷자리 같은 정보가 남아 있다.
 *
 * 동기 처리다. 실측 응답이 5~26초라 사용자가 결과를 보고 바로 지출 등록으로 이어갈 수 있다.
 * 대신 Gemini 호출은 트랜잭션 밖에서 한다 — 그 시간 동안 DB 커넥션을 물고 있으면
 * 동시 업로드 몇 건에 풀이 마른다(일일 리포트와 같은 원칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptOcrJobRepository receiptOcrJobRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg");
    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10MB

    /** 업로드 → OCR → 결과. 실패해도 job 은 FAILED 로 남겨 이력을 추적한다. */
    public ReceiptOcrResponse process(Long userId, MultipartFile file) {
        byte[] image = validateAndRead(file);
        String mimeType = file.getContentType();

        // 1) 접수 기록 — 짧은 쓰기
        ReceiptOcrJob job = transactionTemplate.execute(tx ->
                receiptOcrJobRepository.save(ReceiptOcrJob.builder()
                        .userId(userId)
                        .provider(OcrProvider.GEMINI)
                        .status(OcrStatus.PROCESSING)
                        .requestedAt(LocalDateTime.now())
                        .build()));

        // 2) 외부 호출 — 트랜잭션 밖
        String raw;
        try {
            raw = geminiClient.extractReceipt(image, mimeType);
        } catch (Exception e) {
            return fail(job.getId(), "AI 인식 호출에 실패했습니다.", e);
        }

        ExtractedReceipt extracted;
        try {
            extracted = objectMapper.readValue(raw, ExtractedReceipt.class);
        } catch (Exception e) {
            log.warn("영수증 JSON 파싱 실패 — jobId={}, raw={}", job.getId(),
                    raw == null ? "null" : raw.substring(0, Math.min(200, raw.length())));
            return fail(job.getId(), "인식 결과를 해석하지 못했습니다.", e);
        }

        // 3) 결과 저장 — 짧은 쓰기
        return transactionTemplate.execute(tx -> {
            ReceiptOcrJob fresh = receiptOcrJobRepository.findById(job.getId()).orElseThrow();
            fresh.markSuccess(raw, extracted.totalAmount(), parseDate(extracted.date()),
                    trim(extracted.itemLabel(), 100), LocalDateTime.now());
            // 인식 성공 건수가 바뀌었으므로 칭호 조건을 다시 본다
            eventPublisher.publishEvent(new TitleCheckEvent(userId, "RECEIPT_SCANNED"));
            return ReceiptOcrResponse.of(fresh, extracted);
        });
    }

    @Transactional(readOnly = true)
    public List<ReceiptOcrResponse> listMine(Long userId) {
        return receiptOcrJobRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
                .map(ReceiptOcrResponse::summary)
                .toList();
    }

    /** 단건 조회. 성공 건이면 저장해 둔 원문을 다시 파싱해 상세까지 돌려준다. */
    @Transactional(readOnly = true)
    public ReceiptOcrResponse get(Long userId, Long jobId) {
        ReceiptOcrJob job = receiptOcrJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "인식 기록을 찾을 수 없습니다"));
        if (!job.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 기록만 조회할 수 있습니다");
        }

        ExtractedReceipt extracted = null;
        if (job.getExtractedText() != null) {
            try {
                extracted = objectMapper.readValue(job.getExtractedText(), ExtractedReceipt.class);
            } catch (Exception e) {
                log.warn("저장된 영수증 원문 파싱 실패 — jobId={}", jobId);
            }
        }
        return ReceiptOcrResponse.of(job, extracted);
    }

    // ───── 내부 ─────

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "영수증 이미지를 첨부해주세요");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지는 10MB 이하만 업로드할 수 있습니다");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PNG 또는 JPEG 이미지만 올릴 수 있습니다");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 읽을 수 없습니다");
        }
    }

    /** 실패를 기록하고 응답으로 변환. 예외를 밖으로 던지지 않아 이력이 남는다. */
    private ReceiptOcrResponse fail(Long jobId, String message, Exception cause) {
        log.error("영수증 OCR 실패 — jobId={}", jobId, cause);
        return transactionTemplate.execute(tx -> {
            ReceiptOcrJob fresh = receiptOcrJobRepository.findById(jobId).orElseThrow();
            fresh.markFailed(message, LocalDateTime.now());
            return ReceiptOcrResponse.of(fresh, null);
        });
    }

    /** 모델이 형식을 어긋나게 줄 수 있으므로 파싱 실패는 null 로 흘린다(부분 인식 허용). */
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            log.warn("영수증 날짜 파싱 실패 — raw={}", raw);
            return null;
        }
    }

    /** extracted_item 은 VARCHAR(100) — 길면 잘라 넣는다. */
    private String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
