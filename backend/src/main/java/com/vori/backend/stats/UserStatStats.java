package com.vori.backend.stats;

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
@Table(name = "user_stat_stats")
@IdClass(UserStatStatsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserStatStats {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "stat_type",
        columnDefinition = "ENUM('ENERGY','CHARM','IQ','ENDURANCE')")
    private StatType statType;

    @Column(name = "mean_ema", nullable = false, precision = 12, scale = 2)
    private BigDecimal meanEma;

    @Column(name = "stddev_ema", nullable = false, precision = 12, scale = 2)
    private BigDecimal stddevEma;

    @Column(name = "sample_count", nullable = false)
    @Builder.Default
    private Integer sampleCount = 0;

    @Column(name = "updated_at", nullable = false,
        columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
