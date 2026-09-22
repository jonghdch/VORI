package com.vori.backend.judgment;

import com.vori.backend.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-judgments")
@RequiredArgsConstructor
public class DailyJudgmentController {
    private final DailyJudgmentService dailyJudgmentService;

    @GetMapping("/today")
    public ResponseEntity<DailyJudgmentResponse> today(
            @AuthenticationPrincipal UserPrincipal principal) {
        return dailyJudgmentService.getToday(principal.getUser().getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/today")
    public DailyJudgmentResponse judgeToday(@AuthenticationPrincipal UserPrincipal principal) {
        return dailyJudgmentService.judgeToday(principal.getUser());
    }
}
