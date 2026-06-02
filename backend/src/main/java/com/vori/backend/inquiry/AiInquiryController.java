package com.vori.backend.inquiry;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.inquiry.dto.AnswerRequest;
import com.vori.backend.inquiry.dto.InquiryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class AiInquiryController {

    private final AiInquiryService aiInquiryService;

    /** Step 2 — 그 날짜에 발생한 미답변 AI 질문 목록. */
    @GetMapping
    public ResponseEntity<List<InquiryResponse>> listPending(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(aiInquiryService.listPendingByDate(principal.getUser().getId(), date));
    }

    @PostMapping("/{id}/answer")
    public ResponseEntity<Void> answer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AnswerRequest req
    ) {
        aiInquiryService.answerInquiry(id, principal.getUser().getId(), req);
        return ResponseEntity.ok().build();
    }
}
