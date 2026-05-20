package com.vori.backend.category;

import com.vori.backend.category.dto.CategoryTreeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 활성 카테고리 트리. 지출 입력 폼에서 대분류 → 상세 셀렉트를 만들 때 사용.
     * 인증된 사용자만 호출 가능 (SecurityConfig 기본 정책).
     */
    @GetMapping
    public List<CategoryTreeResponse> getCategories() {
        return categoryService.getActiveTree();
    }
}
