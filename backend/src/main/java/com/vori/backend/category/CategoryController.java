package com.vori.backend.category;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.category.dto.CategorizeRequest;
import com.vori.backend.category.dto.CategorizeResponse;
import com.vori.backend.category.dto.CategoryTreeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CategorizeService categorizeService;

    /**
     * 활성 카테고리 트리. 지출 입력 폼에서 대분류 → 상세 셀렉트를 만들 때 사용.
     * 인증된 사용자만 호출 가능 (SecurityConfig 기본 정책).
     */
    @GetMapping
    public List<CategoryTreeResponse> getCategories() {
        return categoryService.getActiveTree();
    }

    /**
     * 사용자 입력 → leaf 자동 추론. 본인이 같은 이름으로 저장한 기록이 있으면 그 카테고리를 먼저 쓴다.
     * 매칭 실패(threshold 미달·서비스 미준비·Gemini 미연결) 시 "기타 생활" 폴백 leaf 반환
     * → 자동 분류가 안 돼도 사용자가 입력을 이어갈 수 있다 (다음 단계 진행 가능).
     */
    @PostMapping("/categorize")
    public CategorizeResponse categorize(@AuthenticationPrincipal UserPrincipal principal,
                                         @Valid @RequestBody CategorizeRequest req) {
        Long userId = principal != null ? principal.getId() : null;
        CategorizeService.Result r = categorizeService.categorizeOrFallback(userId, req.name());
        if (r == null) return CategorizeResponse.empty();
        return new CategorizeResponse(r.leafId(), r.leafName(), r.parentId(), r.parentName(), r.score(),
                r.source().name());
    }
}
