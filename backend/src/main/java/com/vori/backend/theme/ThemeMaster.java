package com.vori.backend.theme;

import com.vori.backend.title.Title;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 마이룸 가구 테마 (마스터 데이터). 같은 테마 가구를 required_count 이상 모으면 세트 보너스 발동.
 * unlock_title_id 가 박힌 테마는 그 칭호(titles.id) 보유 사용자만 해제 가능.
 * 칭호는 id 로 잇는다 — 이름으로 이으면 칭호 이름을 고치는 순간 해금이 조용히 끊긴다(V15).
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

    /** 해금 조건 칭호. null 이면 조건 없이 누구나 쓴다. 판정은 id 로, 표시는 name 으로. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unlock_title_id")
    private Title unlockTitle;

    public Long getUnlockTitleId() {
        return unlockTitle == null ? null : unlockTitle.getId();
    }

    /** 화면 안내용("'절약 새싹' 칭호를 획득해야…"). 응답 DTO 의 unlockTitleName 이 이걸 쓴다. */
    public String getUnlockTitleName() {
        return unlockTitle == null ? null : unlockTitle.getName();
    }
}
