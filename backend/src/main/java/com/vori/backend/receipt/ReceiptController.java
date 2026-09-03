package com.vori.backend.receipt;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.receipt.dto.ReceiptOcrResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 영수증 사진 인식. 인증 필요(세션), 본인 데이터만.
 *
 * 인식 결과를 그대로 지출로 만들지는 않는다 — 사용자가 화면에서 값을 확인·수정한 뒤
 * 기존 지출 등록 API(POST /api/expenses)를 호출하는 흐름이다.
 */
@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    /**
     * POST /api/receipts (multipart/form-data, file)
     * 업로드한 영수증에서 상호·날짜·금액·품목을 추출해 돌려준다. 동기 처리(보통 5~10초).
     */
    @PostMapping(consumes = "multipart/form-data")
    public ReceiptOcrResponse upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        return receiptService.process(principal.getUser().getId(), file);
    }

    /** GET /api/receipts — 내 인식 이력(최신순). */
    @GetMapping
    public List<ReceiptOcrResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return receiptService.listMine(principal.getUser().getId());
    }

    /** GET /api/receipts/{id} — 단건. 성공 건이면 품목 목록까지 함께 반환. */
    @GetMapping("/{id}")
    public ReceiptOcrResponse get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return receiptService.get(principal.getUser().getId(), id);
    }
}
