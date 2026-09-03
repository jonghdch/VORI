package com.vori.backend.budget;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.budget.dto.BudgetResponse;
import com.vori.backend.budget.dto.BudgetUpsertRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 월 지출 예산. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    /**
     * GET /api/budgets?yearMonth=2026-09
     * 예산과 그 달 사용 현황(사용액·잔액·사용률). 예산 미설정이면 budgetSet=false 로 온다.
     */
    @GetMapping
    public BudgetResponse get(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String yearMonth
    ) {
        return budgetService.get(principal.getUser().getId(), yearMonth);
    }

    /**
     * PUT /api/budgets — 설정 또는 변경.
     * 월당 1건이라 생성·수정을 나누지 않았다. 프론트가 존재 여부를 먼저 조회할 필요가 없다.
     */
    @PutMapping
    public BudgetResponse upsert(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BudgetUpsertRequest req
    ) {
        return budgetService.upsert(principal.getUser().getId(), req);
    }

    /** DELETE /api/budgets/2026-09 — 예산 해제. 없어도 204(멱등). */
    @DeleteMapping("/{yearMonth}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String yearMonth
    ) {
        budgetService.delete(principal.getUser().getId(), yearMonth);
    }
}
