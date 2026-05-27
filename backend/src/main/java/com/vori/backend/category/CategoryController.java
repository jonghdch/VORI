package com.vori.backend.category;

import com.vori.backend.category.dto.CategorizeRequest;
import com.vori.backend.category.dto.CategorizeResponse;
import com.vori.backend.category.dto.CategoryTreeResponse;
import lombok.RequiredArgsConstructor;
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
     * 사용자 입력 → 가장 가까운 leaf 자동 추론.
     * 매칭 실패(threshold 미달 또는 서비스 미준비) 시 leafId=null 반환 → 프론트에서 "기타" 처리.
     */
    @PostMapping("/categorize")
    public CategorizeResponse categorize(@RequestBody CategorizeRequest req) {
        CategorizeService.Result r = categorizeService.categorize(req.name());
        if (r == null) return CategorizeResponse.empty();
        return new CategorizeResponse(r.leafId(), r.leafName(), r.parentId(), r.parentName(), r.score());
    }
}
