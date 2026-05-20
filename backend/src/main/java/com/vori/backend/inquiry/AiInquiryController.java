package com.vori.backend.inquiry;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.inquiry.dto.AnswerRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class AiInquiryController {

    private final AiInquiryService aiInquiryService;

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
