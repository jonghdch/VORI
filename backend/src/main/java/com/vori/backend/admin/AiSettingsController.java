package com.vori.backend.admin;

import com.vori.backend.admin.dto.AiSettingsResponse;
import com.vori.backend.admin.dto.AiSettingsUpdateRequest;
import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.gemini.AiSwitches;
import com.vori.backend.gemini.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 화면 「AI 사용량」 — Gemini 한도 상태를 보고, 없어도 되는 AI 문장 생성(질문 문구·일일 코멘트)을 끈다.
 * /api/admin/** 라서 SecurityConfig 가 hasRole("ADMIN") 으로 보호한다.
 * 스위치는 메모리에만 둔다(AiSwitches) — 재시작하면 환경변수 기본값으로 돌아간다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai-settings")
@RequiredArgsConstructor
public class AiSettingsController {

    private final AiSwitches aiSwitches;
    private final GeminiClient geminiClient;

    @GetMapping
    public AiSettingsResponse get() {
        return current();
    }

    /** 보낸 값만 바꾼다(null 은 그대로). */
    @PutMapping
    public AiSettingsResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestBody AiSettingsUpdateRequest req) {
        if (req.questionWording() != null) aiSwitches.setQuestionWording(req.questionWording());
        if (req.dailyComment() != null) aiSwitches.setDailyComment(req.dailyComment());
        log.info("AI 스위치 변경 — adminId={}, 질문 문구={}, 일일 코멘트={}",
                principal != null ? principal.getId() : null,
                aiSwitches.questionWording(), aiSwitches.dailyComment());
        return current();
    }

    private AiSettingsResponse current() {
        return new AiSettingsResponse(aiSwitches.questionWording(), aiSwitches.dailyComment(),
                geminiClient.quotaStatus());
    }
}
