package com.vori.backend.pettitle;

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
 * 펫 칭호 마스터. 업적(titles)과 달리 유저가 아니라 펫이 얻는다 — 펫마다 처음부터 다시 도전한다.
 * 칭호 과제는 배웅 조건과 상관없는 도전 목표다.
 */
@Entity
@Table(name = "pet_titles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PetTitle {

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
    @Column(name = "metric_type", nullable = false, columnDefinition = "ENUM('LEVEL','INTERACTIONS','AI_ANSWERS','CHARM_BONUS')")
    private PetTitleMetricType metricType;

    @Column(nullable = false)
    private Long threshold;

    @Column(nullable = false)
    private Boolean enabled;

    // 히든 칭호 — 따기 전에는 조건을 가리고 달성률만 보여 준다(PetTitleService.item).
    @Column(nullable = false)
    private Boolean hidden;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public boolean isHidden() {
        return Boolean.TRUE.equals(hidden);
    }

    public long currentOf(PetTitleProgress progress) {
        return metricType.currentOf(progress);
    }

    public boolean isAchieved(PetTitleProgress progress) {
        return currentOf(progress) >= threshold;
    }

    public int progressPct(PetTitleProgress progress) {
        if (threshold == null || threshold <= 0) return 100;
        return (int) Math.min(100, currentOf(progress) * 100 / threshold);
    }
}
