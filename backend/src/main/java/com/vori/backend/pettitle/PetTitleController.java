package com.vori.backend.pettitle;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.pettitle.dto.EquipTitleRequest;
import com.vori.backend.pettitle.dto.PetTitleBoardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 펫 칭호 조회·장착. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequiredArgsConstructor
public class PetTitleController {

    private final PetTitleService petTitleService;

    /** GET /api/pet-titles — 키우는 펫의 칭호 과제와 진행도. 펫이 없으면 petId null + 과제 0%. */
    @GetMapping("/api/pet-titles")
    public PetTitleBoardResponse board(@AuthenticationPrincipal UserPrincipal principal) {
        return petTitleService.board(principal.getId());
    }

    /** PUT /api/pets/{id}/equipped-title — 칭호 장착. body {awardId}, null 이면 장착 해제. 분양한 펫이면 409. */
    @PutMapping("/api/pets/{id}/equipped-title")
    public PetTitleBoardResponse equip(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody EquipTitleRequest request
    ) {
        return petTitleService.equip(principal.getId(), id, request.awardId());
    }
}
