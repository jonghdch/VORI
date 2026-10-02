package com.vori.backend.title;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.title.dto.EquipAchievementsRequest;
import com.vori.backend.title.dto.TitleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 업적 조회(코드 이름은 옛 "칭호" 그대로). 인증 필요(세션), 본인 데이터만.
 * 업적 장착은 PUT /api/titles/equipped, 펫 칭호 장착은 PUT /api/pets/{id}/equipped-title.
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
        return titleService.list(principal.getId());
    }

    /** PUT /api/titles/equipped — 업적 장착(최대 3개, 칸 순서대로). body {ids:[...]}, 빈 목록이면 모두 장착 해제. */
    @PutMapping("/equipped")
    public List<TitleResponse> equip(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody EquipAchievementsRequest request
    ) {
        return titleService.equip(principal.getId(), request.ids());
    }
}
