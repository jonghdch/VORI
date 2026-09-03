package com.vori.backend.title;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.title.dto.TitleActivateRequest;
import com.vori.backend.title.dto.TitleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 칭호 조회·장착. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequestMapping("/api/titles")
@RequiredArgsConstructor
public class TitleController {

    private final TitleService titleService;

    /**
     * GET /api/titles
     * 획득한 칭호와 아직 못 딴 칭호를 함께 반환한다(미획득은 진행률 포함, 달성 근접 순).
     * 조회 시점에 조건을 다시 평가하므로, 이벤트를 놓쳤더라도 여기서 지급된다.
     */
    @GetMapping
    public List<TitleResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return titleService.list(principal.getUser().getId());
    }

    /** PUT /api/titles/active — 칭호 장착. titleId 를 null 로 보내면 해제. */
    @PutMapping("/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setActive(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody TitleActivateRequest req
    ) {
        titleService.setActive(principal.getUser().getId(), req.titleId());
    }
}
