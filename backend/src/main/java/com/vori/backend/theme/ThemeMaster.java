package com.vori.backend.theme;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 마이룸 가구 테마 (마스터 데이터). 같은 테마 가구를 required_count 이상 모으면 세트 보너스 발동.
 * unlock_title_name 가 박힌 테마는 그 칭호 보유 사용자만 해제 가능.
 */
@Entity
@Table(name = "theme_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ThemeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "set_bonus_pct", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal setBonusPct = BigDecimal.ZERO;

    @Column(name = "required_count", columnDefinition = "TINYINT")
    @Builder.Default
    private Integer requiredCount = 3;

    @Column(name = "unlock_title_name", length = 50)
    private String unlockTitleName;
}
