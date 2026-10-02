package com.vori.backend.report;

import com.vori.backend.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

/** 월간(보이는) 리포트 정산 기록. 인증 필요, 본인 것만. */
@RestController
@RequestMapping("/api/monthly-reports")
@RequiredArgsConstructor
public class MonthlyReportController {

    private final MonthlyReportService monthlyReportService;

    /** GET /api/monthly-reports — 정산된 리포트 목록, 최신 달부터. */
    @GetMapping
    public List<MonthlyReportResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return monthlyReportService.list(principal.getId());
    }

    /** GET /api/monthly-reports/unread — 안 본 가장 최근 리포트. 없으면 본문 없는 200. */
    @GetMapping("/unread")
    public ResponseEntity<MonthlyReportResponse> unread(@AuthenticationPrincipal UserPrincipal principal) {
        return monthlyReportService.latestUnread(principal.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().build());
    }

    /** POST /api/monthly-reports/{yearMonth}/read — 리포트 화면에서 그 달을 열면 부른다. */
    @PostMapping("/{yearMonth}/read")
    public void read(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String yearMonth) {
        monthlyReportService.markRead(principal.getId(), yearMonth);
    }

    /** POST /api/monthly-reports/generate?month=2026-09 — 본인 리포트 즉시 정산(시연·확인용). 생략하면 이번 달. */
    @PostMapping("/generate")
    public void generate(@AuthenticationPrincipal UserPrincipal principal,
                         @RequestParam(required = false) String month) {
        monthlyReportService.settle(principal.getId(), month == null ? YearMonth.now() : YearMonth.parse(month));
    }
}
