package com.vori.backend.home;

import com.vori.backend.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 대시보드 요약. 인증 필요 (세션). 본인 데이터만.
 */
@RestController
@RequestMapping("/api/users/me/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public ResponseEntity<HomeSummaryResponse> home(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(homeService.getSummary(principal.getUser().getId()));
    }
}
