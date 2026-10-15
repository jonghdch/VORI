package com.vori.backend.judgment;

import com.vori.backend.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/daily-judgments")
@RequiredArgsConstructor
public class DailyJudgmentController {
    private final DailyJudgmentService dailyJudgmentService;

    @PostMapping
    public DailyJudgmentResponse judgeDate(@AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        return dailyJudgmentService.judgeDate(principal.getId(), principal.getRole(), date);
    }

    @GetMapping("/today")
    public ResponseEntity<DailyJudgmentResponse> today(
            @AuthenticationPrincipal UserPrincipal principal) {
        return dailyJudgmentService.getToday(principal.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public ResponseEntity<DailyJudgmentResponse> getByDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        return dailyJudgmentService.getByDate(principal.getId(), date)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/month")
    public List<DailyJudgmentResponse> getByMonth(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam String month) {
        return dailyJudgmentService.getByMonth(principal.getId(), YearMonth.parse(month));
    }

    /** 예외 지출 사유 입력을 마쳤거나 건너뛰었을 때 — 그날 판정을 확정하고 보상을 지급한다 (docs/judgment-flow.md ③). */
    @PostMapping("/finalize")
    public DailyJudgmentResponse finalizeDate(@AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        return dailyJudgmentService.finalizeDate(principal.getId(), principal.getRole(), date);
    }

    @PostMapping("/today")
    public DailyJudgmentResponse judgeToday(@AuthenticationPrincipal UserPrincipal principal) {
        return dailyJudgmentService.judgeToday(principal.getId(), principal.getRole());
    }
}
