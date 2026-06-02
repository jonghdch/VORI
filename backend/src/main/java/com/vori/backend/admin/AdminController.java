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
import com.vori.backend.user.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
