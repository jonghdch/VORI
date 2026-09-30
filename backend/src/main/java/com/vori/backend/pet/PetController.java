package com.vori.backend.pet;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.pet.dto.PetInteractionResponse;
import com.vori.backend.pet.dto.PetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 펫 조회·분양·상호작용. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    /** GET /api/pets/active — 현재 키우는 펫. 없으면 본문 null(200). */
    @GetMapping("/active")
    public PetResponse active(@AuthenticationPrincipal UserPrincipal principal) {
        return petService.getActive(principal.getId());
    }

    /** GET /api/pets — 보유·분양 이력 전체 (최신순). */
    @GetMapping
    public List<PetResponse> all(@AuthenticationPrincipal UserPrincipal principal) {
        return petService.listAll(principal.getId());
    }

    /** POST /api/pets/active/interact — 키우는 펫과 상호작용 1회. 1% 확률로 매력 +1. */
    @PostMapping("/active/interact")
    public PetInteractionResponse interact(@AuthenticationPrincipal UserPrincipal principal) {
        return petService.interact(principal.getId());
    }

    /** POST /api/pets/{id}/release — 성체 펫 분양 → 게임머니 획득. */
    @PostMapping("/{id}/release")
    public PetResponse release(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return petService.release(principal.getId(), id);
    }
}
