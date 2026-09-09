package com.vori.backend.title;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 칭호 마스터 데이터. 운영 데이터는 DB 에 두고, metricType 만 코드의 계산 로직과 연결한다.
 */
@Entity
@Table(name = "titles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Title {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, columnDefinition = "ENUM('TOTAL_SAVED','EXPENSE_COUNT','GOALS_ACHIEVED','PETS_RELEASED','S_TIER_PETS','AI_ANSWERS','RECEIPT_SCANS')")
    private TitleMetricType metricType;

    @Column(nullable = false)
    private Long threshold;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public long currentOf(TitleProgress progress) {
        return metricType.currentOf(progress);
    }

    public boolean isAchieved(TitleProgress progress) {
        return currentOf(progress) >= threshold;
    }

    public int progressPct(TitleProgress progress) {
        if (threshold == null || threshold <= 0) return 100;
        return (int) Math.min(100, currentOf(progress) * 100 / threshold);
    }
}
