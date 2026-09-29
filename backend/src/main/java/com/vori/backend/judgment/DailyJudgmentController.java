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
        return dailyJudgmentService.judgeDate(principal.getUser(), date);
    }

    @GetMapping("/today")
    public ResponseEntity<DailyJudgmentResponse> today(
            @AuthenticationPrincipal UserPrincipal principal) {
        return dailyJudgmentService.getToday(principal.getUser().getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public ResponseEntity<DailyJudgmentResponse> getByDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        return dailyJudgmentService.getByDate(principal.getUser().getId(), date)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/month")
    public List<DailyJudgmentResponse> getByMonth(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam String month) {
        return dailyJudgmentService.getByMonth(principal.getUser().getId(), YearMonth.parse(month));
    }

    @PostMapping("/today")
    public DailyJudgmentResponse judgeToday(@AuthenticationPrincipal UserPrincipal principal) {
        return dailyJudgmentService.judgeToday(principal.getUser());
    }
}
