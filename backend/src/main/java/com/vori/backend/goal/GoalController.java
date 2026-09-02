package com.vori.backend.goal;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.goal.dto.GoalCreateRequest;
import com.vori.backend.goal.dto.GoalResponse;
import com.vori.backend.goal.dto.GoalUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 절약 목표 CRUD. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    /** GET /api/goals?yearMonth=2026-09 — 그 달 목표 + 진행률. */
    @GetMapping
    public List<GoalResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String yearMonth
    ) {
        return goalService.list(principal.getUser().getId(), yearMonth);
    }

    /** POST /api/goals — 목표 생성. 같은 달·같은 대상이 이미 있으면 409. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody GoalCreateRequest req
    ) {
        return goalService.create(principal.getUser().getId(), req);
    }

    /** PATCH /api/goals/{id} — 목표 금액 수정 / 포기 처리. */
    @PatchMapping("/{id}")
    public GoalResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody GoalUpdateRequest req
    ) {
        return goalService.update(principal.getUser().getId(), id, req);
    }

    /** DELETE /api/goals/{id} */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        goalService.delete(principal.getUser().getId(), id);
    }
}
