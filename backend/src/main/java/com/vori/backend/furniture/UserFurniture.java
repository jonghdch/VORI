package com.vori.backend.furniture;

import com.vori.backend.common.StatType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_furniture")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserFurniture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
        columnDefinition = "ENUM('BED','WALLPAPER','FLOOR','MIRROR','VANITY','PICTURE','BOARD','SHELF','DRAWER','COMPUTER')")
    private FurnitureCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "stat_target", nullable = false,
        columnDefinition = "ENUM('ENERGY','CHARM','IQ','ENDURANCE')")
    private StatType statTarget;

    @Column(name = "release_bonus_pct", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal releaseBonusPct = BigDecimal.ZERO;

    @Column(name = "theme_id")
    private Long themeId;

    @Column(name = "price_game_money", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer priceGameMoney;

    @Column(name = "position_x")
    private Short positionX;

    @Column(name = "position_y")
    private Short positionY;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;
}
