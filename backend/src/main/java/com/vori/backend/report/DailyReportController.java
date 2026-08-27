package com.vori.backend.report;

import com.vori.backend.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 일일 리포트 조회. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequestMapping("/api/daily-reports")
@RequiredArgsConstructor
public class DailyReportController {

    private final DailyReportService dailyReportService;

    /** GET /api/daily-reports/today — 가장 최근 리포트. 없으면 본문 null(200). */
    @GetMapping("/today")
    public DailyReportResponse today(@AuthenticationPrincipal UserPrincipal principal) {
        return dailyReportService.getLatest(principal.getUser().getId());
    }

    /** GET /api/daily-reports/2026-08-26 — 특정 날짜. */
    @GetMapping("/{date}")
    public DailyReportResponse byDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return dailyReportService.getByDate(principal.getUser().getId(), date);
    }

    /** GET /api/daily-reports?from=2026-08-01&to=2026-08-31 — 기간 목록(최신순). */
    @GetMapping
    public List<DailyReportResponse> range(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return dailyReportService.listRange(principal.getUser().getId(), from, to);
    }

    /** POST /api/daily-reports/{id}/read — 읽음 처리. */
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        dailyReportService.markRead(principal.getUser().getId(), id);
    }

    /**
     * POST /api/daily-reports/generate?date=2026-08-26 — 본인 리포트 즉시 생성.
     * 스케줄러(매일 00:10)를 기다리지 않고 시연·디버깅에서 바로 결과를 보기 위한 것.
     * date 를 안 주면 어제 날짜로 만든다.
     */
    @PostMapping("/generate")
    public DailyReportResponse generateNow(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate target = date != null ? date : LocalDate.now().minusDays(1);
        return dailyReportService.generateNow(principal.getUser().getId(), target);
    }
}
