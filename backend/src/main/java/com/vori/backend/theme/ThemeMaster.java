package com.vori.backend.theme;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
