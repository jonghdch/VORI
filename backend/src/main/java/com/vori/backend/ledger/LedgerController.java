package com.vori.backend.ledger;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.ledger.dto.LedgerSaveRequest;
import com.vori.backend.ledger.dto.LedgerSaveResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 월별 가계부 조회 + 작성 화면 일괄 저장 + 지출 삭제. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    /** GET /api/ledger?yearMonth=2026-06 — 그 달 지출+수입 통합 목록 (날짜 오름차순). */
    @GetMapping
    public List<LedgerResponse> monthly(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String yearMonth
    ) {
        return ledgerService.getMonthly(principal.getId(), yearMonth);
    }

    /**
     * POST /api/ledger/entries — 작성 화면의 지출·수입·저축을 한 번에 저장.
     * 하나라도 실패하면 아무것도 저장되지 않는다.
     */
    @PostMapping("/entries")
    public LedgerSaveResponse saveEntries(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LedgerSaveRequest req
    ) {
        return ledgerService.saveEntries(principal.getId(), req);
    }

    /** DELETE /api/ledger/expenses/{id} — 본인 지출 삭제. */
    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        ledgerService.deleteExpense(principal.getId(), id);
    }
}
