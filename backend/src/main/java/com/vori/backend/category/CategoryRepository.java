package com.vori.backend.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByParentIdIsNullAndName(String name);

    List<Category> findByParentIdIsNullOrderBySortOrderAsc();

    List<Category> findByParentIdOrderBySortOrderAsc(Long parentId);

    // 분류 실패 시 폴백 leaf 조회용 (이름으로). 예: "기타 생활"
    Optional<Category> findFirstByName(String name);
}
