package com.vori.backend.category;

import com.vori.backend.category.dto.CategoryTreeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 활성 카테고리 전체를 대분류 → 상세 2단 트리로 반환.
     * 한 번의 findAll 로 44개 다 읽고 메모리에서 부모·자식 그룹핑 (N+1 회피).
     */
    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getActiveTree() {
        List<Category> all = categoryRepository.findAll().stream()
            .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
            .toList();

        Map<Long, List<Category>> childrenByParent = all.stream()
            .filter(c -> c.getParentId() != null)
            .collect(Collectors.groupingBy(Category::getParentId));

        Comparator<Category> bySort = Comparator.comparingInt(
            c -> c.getSortOrder() == null ? 0 : c.getSortOrder()
        );

        return all.stream()
            .filter(c -> c.getParentId() == null)
            .sorted(bySort)
            .map(parent -> {
                List<CategoryTreeResponse> children = childrenByParent
                    .getOrDefault(parent.getId(), List.of())
                    .stream()
                    .sorted(bySort)
                    .map(CategoryTreeResponse::leaf)
                    .toList();
                return CategoryTreeResponse.parent(parent, children);
            })
            .toList();
    }
}
