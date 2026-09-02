package com.vori.backend.furniture;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.furniture.dto.FurniturePlaceRequest;
import com.vori.backend.furniture.dto.FurnitureProductResponse;
import com.vori.backend.furniture.dto.FurnitureResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 마이룸 가구 상점·보유·배치. 인증 필요(세션), 본인 데이터만.
 */
@RestController
@RequestMapping("/api/furniture")
@RequiredArgsConstructor
public class FurnitureController {

    private final FurnitureService furnitureService;

    /** GET /api/furniture/products — 상점 목록(가격 오름차순). */
    @GetMapping("/products")
    public List<FurnitureProductResponse> products() {
        return furnitureService.listProducts();
    }

    /** GET /api/furniture — 내 보유 가구. 배치된 것부터. */
    @GetMapping
    public List<FurnitureResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return furnitureService.listMine(principal.getUser().getId());
    }

    /** POST /api/furniture/buy?item=BOOKSHELF — 구매. 구매 직후엔 인벤토리 상태. */
    @PostMapping("/buy")
    public FurnitureResponse buy(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam FurnitureCatalog item
    ) {
        return furnitureService.buy(principal.getUser().getId(), item);
    }

    /** PATCH /api/furniture/{id}/place — 마이룸에 배치. 자리가 겹치면 409. */
    @PatchMapping("/{id}/place")
    public FurnitureResponse place(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody FurniturePlaceRequest req
    ) {
        return furnitureService.place(principal.getUser().getId(), id, req);
    }

    /** PATCH /api/furniture/{id}/unplace — 인벤토리로 회수. */
    @PatchMapping("/{id}/unplace")
    public FurnitureResponse unplace(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        return furnitureService.unplace(principal.getUser().getId(), id);
    }
}
