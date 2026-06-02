package com.vori.backend.admin;

import com.vori.backend.admin.dto.AdminUserResponse;
import com.vori.backend.admin.dto.DashboardSummaryResponse;
import com.vori.backend.admin.dto.PageResponse;
import com.vori.backend.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
