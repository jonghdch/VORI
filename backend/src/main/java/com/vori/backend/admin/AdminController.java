package com.vori.backend.admin;

import com.vori.backend.admin.dto.AdminUserResponse;
import com.vori.backend.admin.dto.AiLogResponse;
import com.vori.backend.admin.dto.CategoryStatResponse;
import com.vori.backend.admin.dto.DashboardSummaryResponse;
import com.vori.backend.admin.dto.PageResponse;
import com.vori.backend.admin.dto.SignalRuleResponse;
import com.vori.backend.admin.dto.SignalRuleUpdateRequest;
import com.vori.backend.expense.SignalConfigService;
import com.vori.backend.inquiry.ReasonCategory;
import com.vori.backend.pet.PetStage;
import com.vori.backend.pet.dto.PetResponse;
import com.vori.backend.user.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 어드민 전용 API. 전 경로가 SecurityConfig 에서 hasRole("ADMIN") 으로 보호된다
 * (/api/admin/**). 따라서 컨트롤러 레벨 권한 체크는 생략.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminDashboardService adminDashboardService;
    private final AdminCategoryStatsService adminCategoryStatsService;
    private final AdminAiLogService adminAiLogService;
    private final SignalConfigService signalConfigService;
    private final AdminPetService adminPetService;

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Role role
    ) {
        return ResponseEntity.ok(adminUserService.listUsers(page, size, role));
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<DashboardSummaryResponse> dashboardSummary() {
        return ResponseEntity.ok(adminDashboardService.getSummary());
    }

    // ───── 소비 분석 AI 및 데이터 ─────

    @GetMapping("/category-stats")
    public ResponseEntity<List<CategoryStatResponse>> categoryStats() {
        return ResponseEntity.ok(adminCategoryStatsService.getCategoryStats());
    }

    @GetMapping("/ai-logs")
    public ResponseEntity<PageResponse<AiLogResponse>> aiLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ReasonCategory reason
    ) {
        return ResponseEntity.ok(adminAiLogService.list(page, size, reason));
    }

    @GetMapping("/rationality-rules")
    public ResponseEntity<SignalRuleResponse> getRationalityRules() {
        return ResponseEntity.ok(SignalRuleResponse.from(signalConfigService.getConfig()));
    }

    @PutMapping("/rationality-rules")
    public ResponseEntity<SignalRuleResponse> updateRationalityRules(
            @Valid @RequestBody SignalRuleUpdateRequest req
    ) {
        return ResponseEntity.ok(
                SignalRuleResponse.from(signalConfigService.update(req.zRed(), req.zGreen())));
    }

    // ───── 시연·QA 도구 ─────

    /**
     * POST /api/admin/users/{userId}/pet/grow?stage=ADULT
     * 대상 사용자의 활성 펫을 해당 단계까지 즉시 성장시킨다.
     *
     * 성체까지 정상적으로 키우려면 누적 30만원어치 절약이 필요해 발표 자리에서 분양을
     * 보여줄 수 없다. 진화 임계값을 낮추면 운영 규칙이 왜곡되므로 어드민 경로로만 연다.
     * 올린 스탯은 pet_growth_logs 에 reason=BONUS 로 기록된다.
     */
    @PostMapping("/users/{userId}/pet/grow")
    public ResponseEntity<PetResponse> growUserPet(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "ADULT") PetStage stage
    ) {
        return ResponseEntity.ok(adminPetService.growActivePet(userId, stage));
    }
}
