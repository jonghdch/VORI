package com.vori.backend.theme;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.theme.dto.ThemeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 마이룸 테마 현황. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequestMapping("/api/themes")
@RequiredArgsConstructor
public class ThemeController {

    private final ThemeService themeService;

    /** GET /api/themes — 테마별 해금 여부·배치 개수·세트 발동 여부. 발동 중인 것부터. */
    @GetMapping
    public List<ThemeResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return themeService.list(principal.getUser().getId());
    }
}
