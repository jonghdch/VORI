package com.vori.backend.category;

import com.vori.backend.common.StatType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지출 카테고리 트리 (대분류 + 상세, 깊이 2단).
 * 시드 데이터로 44개 INSERT (CategorySeeder). 사용자 커스텀 카테고리는 MVP 에서 안 받음.
 */
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NULL = 대분류, 값 있음 = 상세 (부모 대분류의 id 참조)
    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "stat_type", nullable = false,
        columnDefinition = "ENUM('ENERGY','CHARM','IQ','ENDURANCE')")
    private StatType statType;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
