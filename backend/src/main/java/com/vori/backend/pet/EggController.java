package com.vori.backend.pet;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.pet.dto.EggProductResponse;
import com.vori.backend.pet.dto.EggResponse;
import com.vori.backend.pet.dto.GachaResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 알 상점·인벤토리·개봉. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequestMapping("/api/eggs")
@RequiredArgsConstructor
public class EggController {

    private final EggService eggService;

    /** GET /api/eggs/products — 상점 상품 목록(가격·확률). 인증 없이도 조회 가능해야 하면 SecurityConfig 조정. */
    @GetMapping("/products")
    public List<EggProductResponse> products() {
        return eggService.listProducts();
    }

    /** GET /api/eggs?unopenedOnly=true — 내 알 목록. 기본은 미개봉만. */
    @GetMapping
    public List<EggResponse> myEggs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "true") boolean unopenedOnly
    ) {
        return eggService.listMyEggs(principal.getUser().getId(), unopenedOnly);
    }

    /** POST /api/eggs/buy?grade=BASIC — 게임머니 차감 후 알 지급. */
    @PostMapping("/buy")
    public EggResponse buy(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam EggGrade grade
    ) {
        return eggService.buy(principal.getUser().getId(), grade);
    }

    /** POST /api/eggs/{id}/open — 가챠 추첨 후 펫 지급. 이미 깐 알이면 409. */
    @PostMapping("/{id}/open")
    public GachaResultResponse open(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return eggService.open(principal.getUser().getId(), id);
    }
}
