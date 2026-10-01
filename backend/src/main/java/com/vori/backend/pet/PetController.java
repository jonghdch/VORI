package com.vori.backend.pet;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.pet.dto.PetChatRequest;
import com.vori.backend.pet.dto.PetChatResponse;
import com.vori.backend.pet.dto.PetInteractionResponse;
import com.vori.backend.pet.dto.PetNameRequest;
import com.vori.backend.pet.dto.PetResponse;
import com.vori.backend.title.TitleMetricType;
import com.vori.backend.title.TitleService;
import com.vori.backend.title.dto.GrantedTitle;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 펫 조회·이름 짓기·분양·상호작용·대화. 인증 필요(세션), 본인 데이터만.
 */
@Slf4j
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final TitleService titleService;
    private final PetChatService petChatService;

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

    /**
     * POST /api/pets/active/interact — 키우는 펫과 상호작용 1회. 1% 확률로 매력 +1.
     *
     * 상호작용 횟수가 칭호 목표치에 닿으면 그 자리에서 지급하고 응답에 싣는다 — 히든 칭호는
     * 목록에 없던 것이라 획득 순간을 알려주지 않으면 사용자가 알 길이 없다.
     * 칭호 평가는 상호작용이 커밋된 뒤에 따로 하고, 실패해도 상호작용 결과는 그대로 돌려준다.
     */
    @PostMapping("/active/interact")
    public PetInteractionResponse interact(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getId();
        PetInteractionResponse result = petService.interact(userId);
        try {
            List<GrantedTitle> newTitles = titleService.grantOnReach(
                    userId, TitleMetricType.PET_INTERACTIONS, result.pet().interactionCount());
            return result.withNewTitles(newTitles);
        } catch (RuntimeException e) {
            log.error("상호작용 칭호 평가 실패 — userId={}", userId, e);
            return result;
        }
    }

    /** GET /api/pets/active/chat — 대화창용 지금 성격과 오늘 남은 대화 횟수. 펫이 없으면 400. */
    @GetMapping("/active/chat")
    public PetChatResponse chatStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return petChatService.status(principal.getId());
    }

    /**
     * POST /api/pets/active/chat — 키우는 펫과 대화 한 마디.
     * 400 = 펫 없음·요청 형식 오류, 429 = 오늘 횟수 소진, 503 = AI 호출 실패(횟수는 차감 안 됨).
     */
    @PostMapping("/active/chat")
    public PetChatResponse chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PetChatRequest request
    ) {
        return petChatService.chat(principal.getId(), request);
    }

    /** PUT /api/pets/{id}/name — 키우는 펫의 이름 짓기(1~10자). 분양한 펫이면 409. */
    @PutMapping("/{id}/name")
    public PetResponse rename(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody PetNameRequest request
    ) {
        return petService.rename(principal.getId(), id, request.name());
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
