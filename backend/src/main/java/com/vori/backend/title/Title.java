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
    @Column(name = "metric_type", nullable = false, columnDefinition = "ENUM('TOTAL_SAVED','EXPENSE_COUNT','GOALS_ACHIEVED','PETS_RELEASED','S_TIER_PETS','AI_ANSWERS','RECEIPT_SCANS','LOGIN_COUNT')")
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

    /** 어드민 생성용. created/updated 는 지금 시각. */
    public static Title create(String code, String name, String description,
                               TitleMetricType metricType, long threshold,
                               boolean enabled, int sortOrder) {
        LocalDateTime now = LocalDateTime.now();
        return new Title(null, code, name, description, metricType, threshold,
                enabled, sortOrder, now, now);
    }

    /**
     * 어드민 수정. code 는 바꾸지 않는다 — user_titles.unlock_condition JSON 과 로그가
     * code 로 기록돼 있어, 바꾸면 과거 획득 근거를 추적할 수 없다.
     */
    public void update(String name, String description, TitleMetricType metricType,
                       long threshold, boolean enabled, int sortOrder) {
        this.name = name;
        this.description = description;
        this.metricType = metricType;
        this.threshold = threshold;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.updatedAt = LocalDateTime.now();
    }

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
