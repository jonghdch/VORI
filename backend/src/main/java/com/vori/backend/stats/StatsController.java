package com.vori.backend.stats;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.stats.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public ResponseEntity<StatsResponse> getMyStats(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(statsService.getMyStats(principal.getUser().getId()));
    }
}
